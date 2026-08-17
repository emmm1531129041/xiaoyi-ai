package org.example.controller;

import java.util.List;

import org.example.dto.ChatResponse;
import org.example.dto.ConversationMessage;
import org.example.dto.ConversationSummary;
import org.example.service.ConversationService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Exposes the application chat and conversation-history endpoints.
 */
@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatClient chatClient;
    private final ConversationService conversationService;

    public ChatController(ChatClient chatClient, ConversationService conversationService) {
        this.chatClient = chatClient;
        this.conversationService = conversationService;
    }

    @PostMapping("/conversations")
    public ConversationSummary createConversation() {
        return conversationService.createConversation();
    }

    @GetMapping("/conversations")
    public List<ConversationSummary> listConversations() {
        return conversationService.listConversations();
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public List<ConversationMessage> getConversationMessages(@PathVariable String conversationId) {
        return conversationService.getMessages(conversationId);
    }

    @DeleteMapping("/conversations/{conversationId}")
    public void deleteConversation(@PathVariable String conversationId) {
        conversationService.deleteConversation(conversationId);
    }

    @GetMapping
    public ChatResponse chat(@RequestParam String message,
                             @RequestParam(defaultValue = "default") String conversationId) {
        conversationService.ensureConversation(conversationId);
        conversationService.addUserMessage(conversationId, message);

        String content = chatClient.prompt()
                .user(message)
                .advisors(advisor -> advisor.param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY,
                        conversationId))
                .call()
                .content();
        conversationService.addAssistantMessage(conversationId, content);
        return new ChatResponse(content);
    }

    /**
     * Streams the model response to the browser as Server-Sent Events (SSE).
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<String> streamChat(@RequestParam String message,
                                   @RequestParam(defaultValue = "default") String conversationId) {
        conversationService.ensureConversation(conversationId);
        conversationService.addUserMessage(conversationId, message);

        StringBuilder assistantResponse = new StringBuilder();
        return chatClient.prompt()
                .user(message)
                .advisors(advisor -> advisor.param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY,
                        conversationId))
                .stream()
                .content()
                .doOnNext(assistantResponse::append)
                .doOnComplete(() -> conversationService.addAssistantMessage(conversationId, assistantResponse.toString()));
    }
}