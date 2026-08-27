package org.example.dto;

import java.time.Instant;
import java.util.List;

/** A message shown in a conversation history, including any persisted attachments. */
public record ConversationMessage(Long id, String role, String content, Instant createdAt,
                                  List<ConversationAttachment> attachments) {
}