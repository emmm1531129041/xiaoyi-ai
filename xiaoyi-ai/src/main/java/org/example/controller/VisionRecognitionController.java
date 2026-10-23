package org.example.controller;

import org.example.service.ConversationService;
import org.example.service.ConversationGenerationCoordinator;
import org.example.service.ConversationGenerationFinalizer;
import org.example.service.ConversationMemoryService;
import org.example.service.VisionRecognitionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/vision")
public class VisionRecognitionController {

    private final VisionRecognitionService visionRecognitionService;
    private final ConversationService conversationService;
    private final ConversationMemoryService conversationMemoryService;
    private final ConversationGenerationCoordinator generationCoordinator;
    private final ConversationGenerationFinalizer generationFinalizer;

    public VisionRecognitionController(VisionRecognitionService visionRecognitionService,
                                       ConversationService conversationService,
                                       ConversationMemoryService conversationMemoryService,
                                       ConversationGenerationCoordinator generationCoordinator,
                                       ConversationGenerationFinalizer generationFinalizer) {
        this.visionRecognitionService = visionRecognitionService;
        this.conversationService = conversationService;
        this.conversationMemoryService = conversationMemoryService;
        this.generationCoordinator = generationCoordinator;
        this.generationFinalizer = generationFinalizer;
    }

    @PostMapping(value = "/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<String> recognize(@RequestParam("file") List<MultipartFile> files,
                                  @RequestParam(value = "prompt", required = false) String prompt,
                                  @RequestParam(defaultValue = "default") String conversationId) {
        ConversationGenerationCoordinator.Generation generation = generationCoordinator.begin(conversationId);
        AtomicBoolean modelStreamStarted = new AtomicBoolean();
        return generation.awaitPreviousTermination()
                .thenMany(Flux.defer(() -> {
                    if (generation.isCancelled()) {
                        return Flux.<String>empty();
                    }
                    try {
                        String question = prompt == null || prompt.isBlank() ? "请识别我上传的图片" : prompt.trim();
                        conversationService.ensureConversation(conversationId);
                        conversationMemoryService.reload(conversationId);
                        conversationService.addUserMessageWithAttachments(conversationId, "[图片识别] " + question, files);
                        StringBuilder assistantResponse = new StringBuilder();
                        modelStreamStarted.set(true);
                        return visionRecognitionService.stream(files, question)
                                .takeUntilOther(generation.cancellation())
                                .doOnNext(assistantResponse::append)
                                .doOnComplete(() -> {
                                    if (!generation.isCancelled()) {
                                        conversationService.addAssistantMessage(conversationId, assistantResponse.toString());
                                    }
                                })
                                .doFinally(signalType -> generationFinalizer.finish(conversationId, generation));
                    } catch (IOException exception) {
                        return Flux.<String>error(exception);
                    }
                }))
                .doFinally(signalType -> {
                    if (!modelStreamStarted.get()) {
                        generationCoordinator.finish(generation);
                    }
                });
    }
}
