package org.example.controller;

import org.example.dto.VisionRecognitionResponse;
import org.example.service.ConversationService;
import org.example.service.VisionRecognitionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/vision")
public class VisionRecognitionController {

    private final VisionRecognitionService visionRecognitionService;
    private final ConversationService conversationService;

    public VisionRecognitionController(VisionRecognitionService visionRecognitionService,
                                       ConversationService conversationService) {
        this.visionRecognitionService = visionRecognitionService;
        this.conversationService = conversationService;
    }

    @PostMapping(value = "/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VisionRecognitionResponse recognize(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "prompt", required = false) String prompt,
            @RequestParam(defaultValue = "default") String conversationId) throws IOException {
        String question = prompt == null || prompt.isBlank() ? "请识别我上传的图片" : prompt.trim();
        conversationService.ensureConversation(conversationId);
        conversationService.addUserMessage(conversationId, "[图片识别] " + question);

        VisionRecognitionResponse response = visionRecognitionService.recognize(file, question);
        conversationService.addAssistantMessage(conversationId, response.content());
        return response;
    }
}