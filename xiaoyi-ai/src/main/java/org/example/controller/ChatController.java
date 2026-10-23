package org.example.controller;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.example.dto.ChatResponse;
import org.example.dto.ConversationMessage;
import org.example.dto.ConversationSummary;
import org.example.service.ConversationService;
import org.example.service.ConversationMemoryService;
import org.example.service.ConversationGenerationCoordinator;
import org.example.service.ConversationGenerationFinalizer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * Exposes the application chat and conversation-history endpoints.
 */
@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatClient chatClient;
    private final ConversationService conversationService;
    private final ConversationMemoryService conversationMemoryService;
    private final ConversationGenerationCoordinator generationCoordinator;
    private final ConversationGenerationFinalizer generationFinalizer;

    public ChatController(ChatClient chatClient, ConversationService conversationService,
                          ConversationMemoryService conversationMemoryService,
                          ConversationGenerationCoordinator generationCoordinator,
                          ConversationGenerationFinalizer generationFinalizer) {
        this.chatClient = chatClient;
        this.conversationService = conversationService;
        this.conversationMemoryService = conversationMemoryService;
        this.generationCoordinator = generationCoordinator;
        this.generationFinalizer = generationFinalizer;
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
    public Mono<Void> deleteConversation(@PathVariable String conversationId) {
        return generationCoordinator.cancelAndAwait(conversationId)
                .timeout(Duration.ofSeconds(5))
                .onErrorMap(TimeoutException.class, exception ->
                        new ResponseStatusException(HttpStatus.CONFLICT, "会话生成任务未能及时停止，请稍后重试删除", exception))
                .then(Mono.<Void>fromRunnable(() -> conversationService.deleteConversation(conversationId)));
    }

    @GetMapping
    public ChatResponse chat(@RequestParam String message,
                             @RequestParam(defaultValue = "default") String conversationId) {
        conversationService.ensureConversation(conversationId);
        //重启后首次处理该会话请求，从MYSQL恢复最近10条消息
        conversationMemoryService.restoreIfNeeded(conversationId);
        //保存用户消息
        conversationService.addUserMessage(conversationId, message);

        //调用大模型获取回复
        String content = chatClient.prompt()
                .user(message)
                .advisors(advisor -> advisor.param(
                        AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY,
                        conversationId))
                .call()
                .content();
        //保存AI回复
        conversationService.addAssistantMessage(conversationId, content);
        return new ChatResponse(content);
    }

    /**
     * Streams the model response to the browser as Server-Sent Events (SSE).
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<String> streamChat(@RequestParam String message,
                                   @RequestParam(defaultValue = "default") String conversationId) {
        ConversationGenerationCoordinator.Generation generation = generationCoordinator.begin(conversationId);
        AtomicBoolean modelStreamStarted = new AtomicBoolean();
        return generation.awaitPreviousTermination()
                .thenMany(Flux.defer(() -> {
                    // A still newer browser request arrived while this one was waiting.
                    if (generation.isCancelled()) {
                        return Flux.<String>empty();
                    }

                    conversationService.ensureConversation(conversationId);
                    // The preceding stream may have been cancelled after its advisor
                    // wrote to memory. Rebuild from MySQL before adding this prompt.
                    conversationMemoryService.reload(conversationId);
                    conversationService.addUserMessage(conversationId, message);
                    modelStreamStarted.set(true);

                    StringBuilder assistantResponse = new StringBuilder();
                    return chatClient.prompt()
                            .user(message)
                            .advisors(advisor -> advisor.param(
                                    AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, conversationId))
                            .stream()
                            .content()
                            .takeUntilOther(generation.cancellation())
                            .doOnNext(assistantResponse::append)
                            .doOnComplete(() -> {
                                if (!generation.isCancelled()) {
                                    conversationService.addAssistantMessage(conversationId, assistantResponse.toString());
                                }
                            })
                            .doFinally(signalType -> {
                                generationFinalizer.finish(conversationId, generation);
                            });
                }))
                // If the browser disconnects while this request is waiting for
                // an older stream, there is no inner stream to run its cleanup.
                .doFinally(signalType -> {
                    if (!modelStreamStarted.get()) {
                        generationCoordinator.finish(generation);
                    }
                });

    }
}
