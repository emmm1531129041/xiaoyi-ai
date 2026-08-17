package org.example.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.protocol.ConnectionOptions;
import com.alibaba.dashscope.protocol.Protocol;
import com.alibaba.dashscope.utils.Constants;
import org.example.dto.ImageGenerationResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Calls Bailian's Qwen Image 3.0 model through the official DashScope Java SDK.
 */
@Service
public class ImageGenerationService {

    private static final Duration IMAGE_GENERATION_TIMEOUT = Duration.ofMinutes(10);

    private final String apiKey;
    private final String model;

    public ImageGenerationService(@Value("${xiaoyi.image.dashscope.api-key}") String apiKey,
                                  @Value("${xiaoyi.image.dashscope.model}") String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    public ImageGenerationResponse generate(String prompt) {
        MultiModalMessage userMessage = MultiModalMessage.builder()
                .role(Role.USER.getValue())
                .content(List.of(Map.of("text", prompt)))
                .build();

        MultiModalConversationParam request = MultiModalConversationParam.builder()
                .apiKey(apiKey)
                .model(model)
                .messages(List.of(userMessage))
                .parameter("prompt_extend", true)
                .build();

        try {
            MultiModalConversationResult result = createImageConversation().call(request);
            return new ImageGenerationResponse(extractImageUrl(result));
        } catch (Exception exception) {
            throw new IllegalStateException("百炼图片生成失败", exception);
        }
    }

    private MultiModalConversation createImageConversation() {
        ConnectionOptions connectionOptions = ConnectionOptions.builder()
                .readTimeout(IMAGE_GENERATION_TIMEOUT)
                .build();
        return new MultiModalConversation(Protocol.HTTP.getValue(), Constants.baseHttpApiUrl, connectionOptions);
    }

    private String extractImageUrl(MultiModalConversationResult result) {
        if (result.getStatusCode() == null || result.getStatusCode() != 200) {
            throw new IllegalStateException("百炼请求失败：" + result.getMessage());
        }
        if (result.getOutput() == null || result.getOutput().getChoices() == null
                || result.getOutput().getChoices().isEmpty()) {
            throw new IllegalStateException("百炼没有返回图片结果");
        }

        List<Map<String, Object>> content = result.getOutput().getChoices().get(0).getMessage().getContent();
        if (content == null || content.isEmpty() || !(content.get(0).get("image") instanceof String imageUrl)) {
            throw new IllegalStateException("百炼返回的图片地址无效");
        }
        return imageUrl;
    }
}