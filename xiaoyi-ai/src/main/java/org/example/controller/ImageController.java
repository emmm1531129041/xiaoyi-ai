package org.example.controller;

import org.example.dto.ImageGenerationRequest;
import org.example.dto.ImageGenerationResponse;
import org.example.service.ImageGenerationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the text-to-image endpoint backed by the Bailian image model.
 */
@RestController
@RequestMapping("/images")
public class ImageController {

    private final ImageGenerationService imageGenerationService;

    public ImageController(ImageGenerationService imageGenerationService) {
        this.imageGenerationService = imageGenerationService;
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.OK)
    public ImageGenerationResponse generate(@RequestBody ImageGenerationRequest request) {
        if (request.prompt() == null || request.prompt().isBlank()) {
            throw new IllegalArgumentException("图片描述不能为空");
        }
        return imageGenerationService.generate(request.prompt().trim());
    }
}