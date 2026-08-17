package org.example.dto;

import java.time.Instant;

/**
 * A message shown in a conversation history.
 */
public record ConversationMessage(String role, String content, Instant createdAt) {
}
