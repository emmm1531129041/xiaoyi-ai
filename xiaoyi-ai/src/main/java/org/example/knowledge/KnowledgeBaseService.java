package org.example.knowledge;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Implements the Transform and Load stages of XiaoYi's knowledge-base pipeline.
 */
@Service
public class KnowledgeBaseService {

    private final SimpleVectorStore vectorStore;
    private final TokenTextSplitter textSplitter;
    private final Path storePath;

    public KnowledgeBaseService(SimpleVectorStore vectorStore,
                                TokenTextSplitter textSplitter,
                                @Value("${xiaoyi.knowledge.store-path}") String storePath) {
        this.vectorStore = vectorStore;
        this.textSplitter = textSplitter;
        this.storePath = Path.of(storePath);
    }

    public boolean hasSnapshot() {
        return Files.isRegularFile(storePath) && storePath.toFile().length() > 0;
    }

    /**
     * T: split extracted Documents into retrieval-sized chunks.
     * L: embed each chunk and save it into SimpleVectorStore's local JSON snapshot.
     */
    public synchronized KnowledgeIndexResult index(String sourceName, List<Document> extractedDocuments) {
        List<Document> documents = normalize(sourceName, extractedDocuments);
        if (documents.isEmpty()) {
            return new KnowledgeIndexResult(sourceName, 0);
        }

        List<Document> chunks = textSplitter.split(documents);
        vectorStore.add(chunks);
        saveSnapshot();
        return new KnowledgeIndexResult(sourceName, chunks.size());
    }

    private List<Document> normalize(String sourceName, List<Document> extractedDocuments) {
        String source = sourceName == null || sourceName.isBlank() ? "unknown-source" : sourceName;
        List<Document> documents = new ArrayList<>();
        for (Document document : extractedDocuments) {
            if (!document.isText() || document.getText() == null || document.getText().isBlank()) {
                continue;
            }
            var metadata = new HashMap<>(document.getMetadata());
            metadata.put("source", source);
            metadata.put("indexedAt", Instant.now().toString());
            documents.add(new Document(document.getText(), metadata));
        }
        return documents;
    }

    private void saveSnapshot() {
        try {
            Path parent = storePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            vectorStore.save(storePath.toFile());
        } catch (IOException exception) {
            throw new IllegalStateException("知识库快照保存失败", exception);
        }
    }
}