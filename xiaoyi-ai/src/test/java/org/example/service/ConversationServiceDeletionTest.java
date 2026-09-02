package org.example.service;

import org.example.entity.ConversationAttachmentEntity;
import org.example.entity.ConversationEntity;
import org.example.repository.ConversationAttachmentRepository;
import org.example.repository.ConversationMessageRepository;
import org.example.repository.ConversationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationServiceDeletionTest {

    @Test
    void deletesFilesThenAttachmentRecordsMessagesConversationAndMemory() {
        ChatMemory chatMemory = mock(ChatMemory.class);
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        ConversationMessageRepository messageRepository = mock(ConversationMessageRepository.class);
        ConversationAttachmentRepository attachmentRepository = mock(ConversationAttachmentRepository.class);
        AttachmentStorageService attachmentStorageService = mock(AttachmentStorageService.class);
        ConversationMemoryService conversationMemoryService = mock(ConversationMemoryService.class);
        ConversationService service = new ConversationService(chatMemory, conversationRepository, messageRepository,
                attachmentRepository, attachmentStorageService, conversationMemoryService);

        ConversationEntity conversation = new ConversationEntity("conversation-1", "测试", Instant.now(), Instant.now());
        ConversationAttachmentEntity attachment = new ConversationAttachmentEntity("conversation-1", 1L,
                "photo.png", "uuid.png", "E:/my-first-web/picture/uuid.png", "image/png", 3, Instant.now());
        when(conversationRepository.findById("conversation-1")).thenReturn(Optional.of(conversation));
        when(attachmentRepository.findByConversationIdOrderByCreatedAtAsc("conversation-1"))
                .thenReturn(List.of(attachment));

        service.deleteConversation("conversation-1");

        var order = inOrder(attachmentStorageService, attachmentRepository, messageRepository,
                conversationRepository, chatMemory);
        order.verify(attachmentStorageService).deleteStoredFiles(List.of(attachment));
        order.verify(attachmentRepository).deleteAll(List.of(attachment));
        order.verify(messageRepository).deleteByConversationId("conversation-1");
        order.verify(conversationRepository).delete(conversation);
        order.verify(chatMemory).clear("conversation-1");
    }
}