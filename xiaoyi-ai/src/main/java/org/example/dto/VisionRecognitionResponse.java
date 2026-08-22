package org.example.dto;

/**
 * Result returned after XiaoYi analyses an uploaded image.
 */
public record VisionRecognitionResponse(String content, String model) {
}