package org.example.dto;

/**
 * Request body for a text-to-image generation request.
 */
public record ImageGenerationRequest(String prompt) {
}