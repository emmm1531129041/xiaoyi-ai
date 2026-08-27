package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "conversation_attachments", indexes = {
        @Index(name = "idx_attachment_conversation", columnList = "conversation_id"),
        @Index(name = "idx_attachment_message", columnList = "message_id")
})
public class ConversationAttachmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false, length = 64)
    private String conversationId;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "stored_name", nullable = false, length = 255, unique = true)
    private String storedName;

    @Column(name = "file_path", nullable = false, length = 1024)
    private String filePath;

    @Column(name = "file_type", nullable = false, length = 255)
    private String fileType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ConversationAttachmentEntity() {
    }

    public ConversationAttachmentEntity(String conversationId, Long messageId, String originalName,
                                        String storedName, String filePath, String fileType,
                                        long fileSize, Instant createdAt) {
        this.conversationId = conversationId;
        this.messageId = messageId;
        this.originalName = originalName;
        this.storedName = storedName;
        this.filePath = filePath;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getConversationId() { return conversationId; }
    public Long getMessageId() { return messageId; }
    public String getOriginalName() { return originalName; }
    public String getStoredName() { return storedName; }
    public String getFilePath() { return filePath; }
    public String getFileType() { return fileType; }
    public long getFileSize() { return fileSize; }
    public Instant getCreatedAt() { return createdAt; }
}