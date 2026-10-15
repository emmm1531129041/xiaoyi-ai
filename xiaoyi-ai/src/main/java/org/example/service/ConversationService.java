package org.example.service;

import org.example.dto.ConversationAttachment;
import org.example.dto.ConversationMessage;
import org.example.dto.ConversationSummary;
import org.example.entity.ConversationAttachmentEntity;
import org.example.entity.ConversationEntity;
import org.example.entity.ConversationMessageEntity;
import org.example.repository.ConversationAttachmentRepository;
import org.example.repository.ConversationMessageRepository;
import org.example.repository.ConversationRepository;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Persists chat history and coordinates attachment storage for local conversations. */
@Service
public class ConversationService {

    private static final String NEW_CONVERSATION_TITLE = "新会话";
    private static final int TITLE_MAX_LENGTH = 18;

    private final ChatMemory chatMemory;
    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final ConversationAttachmentRepository attachmentRepository;
    private final AttachmentStorageService attachmentStorageService;
    private final ConversationMemoryService conversationMemoryService;

    public ConversationService(ChatMemory chatMemory,
                               ConversationRepository conversationRepository,
                               ConversationMessageRepository messageRepository,
                               ConversationAttachmentRepository attachmentRepository,
                               AttachmentStorageService attachmentStorageService,
                               ConversationMemoryService conversationMemoryService) {
        this.chatMemory = chatMemory;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.attachmentRepository = attachmentRepository;
        this.attachmentStorageService = attachmentStorageService;
        this.conversationMemoryService = conversationMemoryService;
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
        List<ConversationMessageEntity> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        if (messages.isEmpty()) {
            return List.of();
        }
        Map<Long, List<ConversationAttachment>> attachmentMap = attachmentRepository
                .findByMessageIdIn(messages.stream().map(ConversationMessageEntity::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(ConversationAttachmentEntity::getMessageId,
                        Collectors.mapping(this::toAttachment, Collectors.toList())));

        return messages.stream()
                .map(message -> new ConversationMessage(message.getId(), message.getRole(), message.getContent(),
                        message.getCreatedAt(), attachmentMap.getOrDefault(message.getId(), List.of())))
                .toList();
    }


    @Transactional
    public void addUserMessage(String conversationId, String content) {
        ConversationEntity conversation = getOrCreate(conversationId);
        updateTitleForFirstMessage(conversation, conversationId, content);
        addMessage(conversation, "user", content);
    }

    @Transactional(rollbackFor = IOException.class)
    public void addUserMessageWithAttachments(String conversationId, String content,
                                              Collection<MultipartFile> files) throws IOException {
        ConversationEntity conversation = getOrCreate(conversationId);
        updateTitleForFirstMessage(conversation, conversationId, content);
        ConversationMessageEntity message = addMessage(conversation, "user", content);
        List<ConversationAttachmentEntity> storedAttachments = new java.util.ArrayList<>();
        try {
            for (MultipartFile file : files) {
                storedAttachments.add(attachmentStorageService.store(
                        conversationId, message.getId(), file
                ));
            }
        } catch (IOException | RuntimeException exception) {
            attachmentStorageService.deleteStoredFiles(storedAttachments);
            throw exception;
        }
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
        ConversationEntity conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在"));
        List<ConversationAttachmentEntity> attachments = attachmentRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);

        attachmentStorageService.deleteStoredFiles(attachments);
        attachmentRepository.deleteAll(attachments);
        messageRepository.deleteByConversationId(conversationId);
        conversationRepository.delete(conversation);
        chatMemory.clear(conversationId);
        conversationMemoryService.forget(conversationId);
    }

    private ConversationMessageEntity addMessage(ConversationEntity conversation, String role, String content) {
        Instant now = Instant.now();
        ConversationMessageEntity message = messageRepository.save(
                new ConversationMessageEntity(conversation.getId(), role, content, now));
        conversation.setUpdatedAt(now);
        conversationRepository.save(conversation);
        return message;
    }

    private void updateTitleForFirstMessage(ConversationEntity conversation, String conversationId, String content) {
        if (!messageRepository.existsByConversationId(conversationId)) {
            conversation.setTitle(createTitle(content));
        }
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

    private ConversationAttachment toAttachment(ConversationAttachmentEntity attachment) {
        return new ConversationAttachment(attachment.getId(), attachment.getOriginalName(), attachment.getFileType(),
                attachment.getFileSize(), attachment.getCreatedAt(), "/chat/attachments/" + attachment.getId() + "/content");
    }

    private String createTitle(String content) {
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= TITLE_MAX_LENGTH
                ? normalized
                : normalized.substring(0, TITLE_MAX_LENGTH) + "…";
    }
}