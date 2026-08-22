package org.example.knowledge;

/**
 * Summarizes one successful Transform + Load operation.
 */
public record KnowledgeIndexResult(String source, int chunkCount) {
}
