package org.example.knowledge;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Implements the Transform and Load stages of XiaoYi's Redis-backed knowledge-base pipeline.
 */
@Service
public class KnowledgeBaseService {

    private final VectorStore vectorStore;
    private final TokenTextSplitter textSplitter;

    public KnowledgeBaseService(VectorStore vectorStore, TokenTextSplitter textSplitter) {
        this.vectorStore = vectorStore;
        this.textSplitter = textSplitter;
    }

    /**
     * T: split extracted Documents into retrieval-sized chunks.
     * L: embed each chunk and persist it in Redis Vector Store.
     */
    public synchronized KnowledgeIndexResult index(String sourceName, List<Document> extractedDocuments) {
        List<Document> documents = normalize(sourceName, extractedDocuments);
        if (documents.isEmpty()) {
            return new KnowledgeIndexResult(sourceName, 0);
        }

        List<Document> chunks = textSplitter.split(documents);
        vectorStore.add(chunks);
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
}