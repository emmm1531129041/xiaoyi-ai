package org.example.dto;

import java.time.Instant;

/**
 * Metadata displayed in the conversation history sidebar.
 */
public record ConversationSummary(String id, String title, Instant updatedAt) {
}
