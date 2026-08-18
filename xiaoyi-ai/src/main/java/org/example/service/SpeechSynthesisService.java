package org.example.service;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import org.example.dto.SpeechSynthesisResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Synthesizes Xiaoyi's answers with the configured Bailian Qwen-TTS voice.
 */
@Service
public class SpeechSynthesisService {

    private final String apiKey;
    private final String model;
    private final String voiceId;
    private final String languageType;

    public SpeechSynthesisService(@Value("${xiaoyi.speech.dashscope.api-key}") String apiKey,
                                  @Value("${xiaoyi.speech.dashscope.model}") String model,
                                  @Value("${xiaoyi.speech.dashscope.voice}") String voiceId,
                                  @Value("${xiaoyi.speech.dashscope.language-type}") String languageType) {
        this.apiKey = apiKey;
        this.model = model;
        this.voiceId = voiceId;
        this.languageType = languageType;
    }

    public SpeechSynthesisResponse synthesize(String text) {
        MultiModalConversationParam request = MultiModalConversationParam.builder()
                .apiKey(apiKey)
                .model(model)
                .text(text)
                // Custom voice IDs are passed through the SDK's generic parameter map.
                .parameter("voice", voiceId)
                .languageType(languageType)
                .build();

        try {
            MultiModalConversationResult result = new MultiModalConversation().call(request);
            return new SpeechSynthesisResponse(extractAudioUrl(result));
        } catch (Exception exception) {
            throw new IllegalStateException("百炼语音合成失败", exception);
        }
    }

    private String extractAudioUrl(MultiModalConversationResult result) {
        if (result.getStatusCode() == null || result.getStatusCode() != 200) {
            throw new IllegalStateException("百炼语音请求失败：" + result.getMessage());
        }
        if (result.getOutput() == null || result.getOutput().getAudio() == null
                || result.getOutput().getAudio().getUrl() == null
                || result.getOutput().getAudio().getUrl().isBlank()) {
            throw new IllegalStateException("百炼没有返回可播放的音频地址");
        }
        return result.getOutput().getAudio().getUrl();
    }
}