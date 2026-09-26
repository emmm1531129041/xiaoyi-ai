package org.example.service;

import org.example.entity.ConversationMessageEntity;
import org.example.repository.ConversationMessageRepository;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Restores a conversation's recent persisted messages into Spring AI's runtime memory.
 * MySQL remains the source of truth; InMemoryChatMemory is only a fast runtime window.
 */
@Service
public class ConversationMemoryService {

    private final ChatMemory chatMemory;
    private final ConversationMessageRepository messageRepository;
    private final Set<String> restoredConversationIds = ConcurrentHashMap.newKeySet();

    public ConversationMemoryService(ChatMemory chatMemory,
                                     ConversationMessageRepository messageRepository) {
        this.chatMemory = chatMemory;
        this.messageRepository = messageRepository;
    }

    @Transactional(readOnly = true)
    public void restoreIfNeeded(String conversationId) {
        if (restoredConversationIds.contains(conversationId)) {
            return;
        }

        synchronized (restoredConversationIds) {
            if (restoredConversationIds.contains(conversationId)) {
                return;
            }
            List<Message> recentMessages = messageRepository
                    .findTop10ByConversationIdOrderByCreatedAtDesc(conversationId)
                    .stream()
                    .sorted(Comparator.comparing(ConversationMessageEntity::getCreatedAt))
                    .map(this::toChatMessage)
                    .flatMap(Optional::stream)
                    .toList();

            chatMemory.clear(conversationId);
            if (!recentMessages.isEmpty()) {
                chatMemory.add(conversationId, recentMessages);
            }
            restoredConversationIds.add(conversationId);
        }
    }

    /** Clears only the restore marker; ConversationService clears the actual ChatMemory. */
    public void forget(String conversationId) {
        restoredConversationIds.remove(conversationId);
    }

    private Optional<Message> toChatMessage(ConversationMessageEntity message) {
        return switch (message.getRole()) {
            case "user" -> Optional.of(new UserMessage(message.getContent()));
            case "assistant" -> Optional.of(new AssistantMessage(message.getContent()));
            default -> Optional.empty();
        };
    }
}