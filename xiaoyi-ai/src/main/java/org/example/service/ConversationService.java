package org.example.service;

import org.example.dto.ConversationMessage;
import org.example.dto.ConversationSummary;
import org.example.entity.ConversationEntity;
import org.example.entity.ConversationMessageEntity;
import org.example.repository.ConversationMessageRepository;
import org.example.repository.ConversationRepository;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Persists chat history for the local interface and keeps model memory separate.
 */
@Service
public class ConversationService {

    private static final String NEW_CONVERSATION_TITLE = "新会话";
    private static final int TITLE_MAX_LENGTH = 18;

    private final ChatMemory chatMemory;
    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;

    public ConversationService(ChatMemory chatMemory,
                               ConversationRepository conversationRepository,
                               ConversationMessageRepository messageRepository) {
        this.chatMemory = chatMemory;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional
    public ConversationSummary createConversation() {
        return toSummary(create(UUID.randomUUID().toString()));
    }

    @Transactional
    public ConversationSummary ensureConversation(String conversationId) {
        return toSummary(getOrCreate(conversationId));
    }

    @Transactional(readOnly = true)
    public List<ConversationSummary> listConversations() {
        return conversationRepository.findAllByOrderByUpdatedAtDesc().stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public List<ConversationMessage> getMessages(String conversationId) {
        getOrCreate(conversationId);
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(message -> new ConversationMessage(message.getRole(), message.getContent(), message.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void addUserMessage(String conversationId, String content) {
        ConversationEntity conversation = getOrCreate(conversationId);
        if (!messageRepository.existsByConversationId(conversationId)) {
            conversation.setTitle(createTitle(content));
        }
        addMessage(conversation, "user", content);
    }

    @Transactional
    public void addAssistantMessage(String conversationId, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        addMessage(getOrCreate(conversationId), "assistant", content);
    }

    @Transactional
    public void deleteConversation(String conversationId) {
        messageRepository.deleteByConversationId(conversationId);
        conversationRepository.deleteById(conversationId);
        chatMemory.clear(conversationId);
    }

    private void addMessage(ConversationEntity conversation, String role, String content) {
        Instant now = Instant.now();
        messageRepository.save(new ConversationMessageEntity(conversation.getId(), role, content, now));
        conversation.setUpdatedAt(now);
        conversationRepository.save(conversation);
    }

    private ConversationEntity getOrCreate(String conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseGet(() -> create(conversationId));
    }

    private ConversationEntity create(String conversationId) {
        Instant now = Instant.now();
        return conversationRepository.save(new ConversationEntity(conversationId, NEW_CONVERSATION_TITLE, now, now));
    }

    private ConversationSummary toSummary(ConversationEntity conversation) {
        return new ConversationSummary(conversation.getId(), conversation.getTitle(), conversation.getUpdatedAt());
    }

    private String createTitle(String content) {
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= TITLE_MAX_LENGTH
                ? normalized
                : normalized.substring(0, TITLE_MAX_LENGTH) + "…";
    }
}