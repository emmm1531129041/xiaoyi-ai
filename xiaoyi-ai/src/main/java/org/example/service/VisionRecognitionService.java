package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.dto.VisionRecognitionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sends images to Qwen-VL for scene understanding, OCR and visual analysis.
 */
@Service
public class VisionRecognitionService {

    private static final String VISION_API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> SUPPORTED_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final String DEFAULT_PROMPT = "请识别这张图片：先简要描述主要内容；若有清晰文字，请提取文字；若是图表、界面或文档，请归纳关键信息。请使用中文，结构清晰。";

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public VisionRecognitionService(
            @Value("${xiaoyi.vision.dashscope.api-key}") String apiKey,
            @Value("${xiaoyi.vision.dashscope.model}") String model) {
        this.restClient = RestClient.create();
        this.apiKey = apiKey;
        this.model = model;
    }

    public VisionRecognitionResponse recognize(MultipartFile file, String prompt) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请先选择一张图片");
        }
        return recognize(file.getBytes(), file.getContentType(), prompt);
    }

    /**
     * Used by the knowledge-base bootstrapper for default image resources.
     */
    public VisionRecognitionResponse recognize(Resource resource, String prompt) throws IOException {
        try (var inputStream = resource.getInputStream()) {
            return recognize(inputStream.readAllBytes(), contentTypeFromFilename(resource.getFilename()), prompt);
        }
    }

    private VisionRecognitionResponse recognize(byte[] imageBytes, String contentType, String prompt) {
        validateImage(imageBytes, contentType);
        String instruction = prompt == null || prompt.isBlank() ? DEFAULT_PROMPT : prompt.trim();
        String imageDataUrl = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);

        Map<String, Object> imageContent = Map.of(
                "type", "image_url",
                "image_url", Map.of("url", imageDataUrl),
                "max_pixels", 1_310_720);
        Map<String, Object> textContent = Map.of("type", "text", "text", instruction);
        Map<String, Object> request = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", "你是小艺的视觉识别模块。只根据图片中可见的信息回答；文字模糊时请明确说明。"),
                        Map.of("role", "user", "content", List.of(textContent, imageContent))));

        JsonNode response;
        try {
            response = restClient.post()
                    .uri(VISION_API_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException("视觉识别服务调用失败（HTTP " + exception.getStatusCode().value() + "）");
        }

        String content = response == null ? "" : response.path("choices").path(0).path("message").path("content").asText();
        if (content.isBlank()) {
            String message = response == null ? "服务没有返回数据" : response.path("error").path("message").asText("未能识别图片");
            throw new IllegalStateException("视觉识别失败：" + message);
        }
        return new VisionRecognitionResponse(content, model);
    }

    private void validateImage(byte[] imageBytes, String contentType) {
        if (imageBytes.length == 0) {
            throw new IllegalArgumentException("图片内容为空");
        }
        if (imageBytes.length > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("图片不能超过 10MB");
        }
        if (contentType == null || !SUPPORTED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("仅支持 JPG、PNG、WEBP 或 GIF 图片");
        }
    }

    private String contentTypeFromFilename(String filename) {
        if (filename == null) {
            throw new IllegalArgumentException("无法识别图片格式");
        }
        String lowercaseFilename = filename.toLowerCase();
        if (lowercaseFilename.endsWith(".jpg") || lowercaseFilename.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lowercaseFilename.endsWith(".png")) {
            return "image/png";
        }
        if (lowercaseFilename.endsWith(".webp")) {
            return "image/webp";
        }
        if (lowercaseFilename.endsWith(".gif")) {
            return "image/gif";
        }
        throw new IllegalArgumentException("仅支持 JPG、PNG、WEBP 或 GIF 图片");
    }
}