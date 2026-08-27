package org.example.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Extracts readable text from a user attachment for one temporary AI analysis.
 * This service never writes user files to the knowledge base.
 */
@Service
public class DocumentAnalysisService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final int MAX_ANALYSIS_TEXT_LENGTH = 30_000;
    private static final Set<String> TEXT_EXTENSIONS = Set.of("txt", "md");
    private static final Set<String> TIKA_EXTENSIONS = Set.of("pdf", "docx", "pptx");

    public ExtractedDocument extract(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请先选择一个文档");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文档不能超过 10MB");
        }

        String filename = file.getOriginalFilename();
        String extension = extensionOf(filename);
        if (!TEXT_EXTENSIONS.contains(extension) && !TIKA_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("仅支持 PDF、DOCX、PPTX、TXT 或 Markdown 文档");
        }

        Resource resource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        DocumentReader reader = TEXT_EXTENSIONS.contains(extension)
                ? new TextReader(resource)
                : new TikaDocumentReader(resource);
        List<Document> documents = reader.read();
        String content = documents.stream()
                .map(Document::getText)
                .filter(text -> text != null && !text.isBlank())
                .collect(java.util.stream.Collectors.joining("\n\n"));
        if (content.isBlank()) {
            throw new IllegalArgumentException("没有从文档中提取到可分析的文字");
        }
        boolean truncated = content.length() > MAX_ANALYSIS_TEXT_LENGTH;
        String textForAnalysis = truncated ? content.substring(0, MAX_ANALYSIS_TEXT_LENGTH) : content;
        return new ExtractedDocument(filename == null || filename.isBlank() ? "未命名文档" : filename,
                textForAnalysis, truncated);
    }

    public List<ExtractedDocument> extract(List<MultipartFile> files) throws IOException {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("请先选择至少一个文档");
        }
        java.util.ArrayList<ExtractedDocument> documents = new java.util.ArrayList<>();
        for (MultipartFile file : files) {
            documents.add(extract(file));
        }
        return List.copyOf(documents);
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex < 0 ? "" : filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    public record ExtractedDocument(String filename, String content, boolean truncated) {
    }
}