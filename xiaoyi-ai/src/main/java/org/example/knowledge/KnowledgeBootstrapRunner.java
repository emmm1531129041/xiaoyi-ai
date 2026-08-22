package org.example.knowledge;

import org.example.dto.VisionRecognitionResponse;
import org.example.service.VisionRecognitionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * E: loads the default knowledge resources at startup.
 */
@Component
public class KnowledgeBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBootstrapRunner.class);
    private static final Set<String> TEXT_EXTENSIONS = Set.of("txt", "md", "csv", "json");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final String IMAGE_KNOWLEDGE_PROMPT = "请提取这张知识资料图片中的文字与结构化要点。保留标题、层级、概念、定义、关系和结论；不要闲聊或补充图片中没有的信息。使用中文。";

    private final KnowledgeBaseService knowledgeBaseService;
    private final VisionRecognitionService visionRecognitionService;
    private final ResourcePatternResolver resourcePatternResolver;
    private final Path bootstrapStatePath;
    private final boolean enabled;

    public KnowledgeBootstrapRunner(KnowledgeBaseService knowledgeBaseService,
                                    VisionRecognitionService visionRecognitionService,
                                    ResourcePatternResolver resourcePatternResolver,
                                    @Value("${xiaoyi.knowledge.bootstrap.state-path}") String bootstrapStatePath,
                                    @Value("${xiaoyi.knowledge.bootstrap.enabled:true}") boolean enabled) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.visionRecognitionService = visionRecognitionService;
        this.resourcePatternResolver = resourcePatternResolver;
        this.bootstrapStatePath = Path.of(bootstrapStatePath);
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (!enabled) {
            log.info("知识库默认资料导入已关闭");
            return;
        }

        Set<String> importedSources = loadImportedSources();
        Resource[] resources = resourcePatternResolver.getResources("classpath*:knowledge/**/*.*");
        int sourceCount = 0;
        int chunkCount = 0;
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null || filename.equalsIgnoreCase("README.md") || importedSources.contains(filename)) {
                continue;
            }
            try {
                List<Document> documents = extract(resource, extensionOf(filename));
                KnowledgeIndexResult result = knowledgeBaseService.index(filename, documents);
                markImported(filename, importedSources);
                sourceCount++;
                chunkCount += result.chunkCount();
                log.info("知识库导入完成：source={}, chunks={}", result.source(), result.chunkCount());
            } catch (IOException | IllegalStateException | IllegalArgumentException exception) {
                log.warn("知识库资料导入失败：source={}, reason={}", filename, exception.getMessage());
            }
        }
        log.info("知识库初始化结束：sources={}, chunks={}", sourceCount, chunkCount);
    }

    private List<Document> extract(Resource resource, String extension) throws IOException {
        if (IMAGE_EXTENSIONS.contains(extension)) {
            VisionRecognitionResponse response = visionRecognitionService.recognize(resource, IMAGE_KNOWLEDGE_PROMPT);
            return List.of(new Document(response.content()));
        }
        DocumentReader reader = TEXT_EXTENSIONS.contains(extension)
                ? new TextReader(resource)
                : new TikaDocumentReader(resource);
        return reader.read();
    }

    private Set<String> loadImportedSources() throws IOException {
        if (!Files.isRegularFile(bootstrapStatePath)) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(Files.readAllLines(bootstrapStatePath, StandardCharsets.UTF_8));
    }

    private void markImported(String source, Set<String> importedSources) throws IOException {
        importedSources.add(source);
        Path parent = bootstrapStatePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(bootstrapStatePath, importedSources, StandardCharsets.UTF_8);
    }

    private String extensionOf(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex < 0 ? "" : filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }
}