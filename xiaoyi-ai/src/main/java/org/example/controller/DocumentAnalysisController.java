package org.example.controller;

import org.example.service.ConversationService;
import org.example.service.DocumentAnalysisService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;

/**
 * Analyzes one user-uploaded document without adding it to the knowledge base.
 */
@RestController
@RequestMapping("/documents")
public class DocumentAnalysisController {

    private final DocumentAnalysisService documentAnalysisService;
    private final ChatClient chatClient;
    private final ConversationService conversationService;

    public DocumentAnalysisController(DocumentAnalysisService documentAnalysisService,
                                      ChatClient chatClient,
                                      ConversationService conversationService) {
        this.documentAnalysisService = documentAnalysisService;
        this.chatClient = chatClient;
        this.conversationService = conversationService;
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<String> analyze(@RequestParam("file") MultipartFile file,
                                @RequestParam(value = "prompt", required = false) String prompt,
                                @RequestParam(defaultValue = "default") String conversationId) throws IOException {
        DocumentAnalysisService.ExtractedDocument document = documentAnalysisService.extract(file);
        String question = prompt == null || prompt.isBlank() ? "请概括这份文档的重点" : prompt.trim();
        conversationService.ensureConversation(conversationId);
        conversationService.addUserMessage(conversationId, "[文档分析：" + document.filename() + "] " + question);

        String context = """
                下面是用户刚刚上传的文档内容，仅用于回答本次问题。
                文件名：%s
                %s
                ---------------------
                %s
                ---------------------
                请根据文档内容回答用户问题；若内容不够，请明确说明。
                """.formatted(document.filename(), document.truncated() ? "注意：文档过长，仅提取了前 30000 个字符。" : "", document.content());
        StringBuilder assistantResponse = new StringBuilder();
        return chatClient.prompt()
                .system(context)
                .user(question)
                .advisors(advisor -> advisor.param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY,
                        conversationId))
                .stream()
                .content()
                .doOnNext(assistantResponse::append)
                .doOnComplete(() -> conversationService.addAssistantMessage(conversationId, assistantResponse.toString()));
    }
}