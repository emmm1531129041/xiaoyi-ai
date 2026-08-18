package org.example.controller;

import org.example.dto.SpeechSynthesisRequest;
import org.example.dto.SpeechSynthesisResponse;
import org.example.service.SpeechSynthesisService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes Xiaoyi's text-to-speech endpoint.
 */
@RestController
@RequestMapping("/speech")
public class SpeechController {

    private final SpeechSynthesisService speechSynthesisService;

    public SpeechController(SpeechSynthesisService speechSynthesisService) {
        this.speechSynthesisService = speechSynthesisService;
    }

    @PostMapping("/synthesize")
    public SpeechSynthesisResponse synthesize(@RequestBody SpeechSynthesisRequest request) {
        if (request.text() == null || request.text().isBlank()) {
            throw new IllegalArgumentException("朗读文本不能为空");
        }
        return speechSynthesisService.synthesize(request.text().trim());
    }
}