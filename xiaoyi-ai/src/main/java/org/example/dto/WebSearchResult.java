package org.example.dto;

/**
 * A concise, model-friendly result returned by the web search tool.
 */
public record WebSearchResult(String title, String url, String content) {
}
