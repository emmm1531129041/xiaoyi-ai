package org.example.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.example.dto.ConversationMessage;
import org.example.dto.ConversationSummary;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

/**
 * Stores conversation metadata and messages for the local chat interface.
 */
@Service
public class ConversationService {

    private static final String NEW_CONVERSATION_TITLE = "新会话";
    private static final int TITLE_MAX_LENGTH = 18;

    private final ChatMemory chatMemory;
    private final Map<String, Conversation> conversations = new ConcurrentHashMap<>();

    public ConversationService(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }

    public ConversationSummary createConversation() {
        return getOrCreate(UUID.randomUUID().toString()).summary();
    }

    public ConversationSummary ensureConversation(String conversationId) {
        return getOrCreate(conversationId).summary();
    }

    public List<ConversationSummary> listConversations() {
        return conversations.values().stream()
                .map(Conversation::summary)
                .sorted(Comparator.comparing(ConversationSummary::updatedAt).reversed())
                .toList();
    }

    public List<ConversationMessage> getMessages(String conversationId) {
        return getOrCreate(conversationId).messages();
    }

    public void addUserMessage(String conversationId, String content) {
        getOrCreate(conversationId).addMessage("user", content, true);
    }

    public void addAssistantMessage(String conversationId, String content) {
        if (!content.isBlank()) {
            getOrCreate(conversationId).addMessage("assistant", content, false);
        }
    }

    public void deleteConversation(String conversationId) {
        conversations.remove(conversationId);
        chatMemory.clear(conversationId);
    }

    private Conversation getOrCreate(String conversationId) {
        return conversations.computeIfAbsent(conversationId, Conversation::new);
    }

    private static final class Conversation {

        private final String id;
        private final List<ConversationMessage> messages = new ArrayList<>();
        private String title = NEW_CONVERSATION_TITLE;
        private Instant updatedAt = Instant.now();

        private Conversation(String id) {
            this.id = id;
        }

        private synchronized void addMessage(String role, String content, boolean isUserMessage) {
            if (isUserMessage && messages.isEmpty()) {
                title = createTitle(content);
            }
            messages.add(new ConversationMessage(role, content, Instant.now()));
            updatedAt = Instant.now();
        }

        private synchronized ConversationSummary summary() {
            return new ConversationSummary(id, title, updatedAt);
        }

        private synchronized List<ConversationMessage> messages() {
            return List.copyOf(messages);
        }

        private static String createTitle(String content) {
            String normalized = content.replaceAll("\\s+", " ").trim();
            return normalized.length() <= TITLE_MAX_LENGTH
                    ? normalized
                    : normalized.substring(0, TITLE_MAX_LENGTH) + "…";
        }
    }
}