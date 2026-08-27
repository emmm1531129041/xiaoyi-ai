package org.example.dto;

import java.time.Instant;

/** Metadata for a file attached to a persisted conversation message. */
public record ConversationAttachment(Long id, String originalName, String fileType,
                                     long fileSize, Instant createdAt, String url) {
}