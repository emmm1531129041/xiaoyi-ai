package org.example.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Defines shared defaults for the XiaoYi chat client.
 */
@Configuration
public class ChatClientConfig {

    private static final String SYSTEM_PROMPT = "你好，我叫小艺，你的智能AI助手，有不会的可以问我~。当用户询问中国城市或区县的当前、今天或实时天气时，必须优先调用 getCurrentWeather 工具；如果用户没有提供地点，请先追问城市。拿到工具结果后，基于实时数据给出简洁、友好的回答，不要编造天气数据。";
    private static final String DEFAULT_CONVERSATION_ID = "xiaoyi-web-chat";
    private static final int CHAT_HISTORY_WINDOW_SIZE = 10;

    @Bean
    ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }

    @Bean
    ChatClient chatClient(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory) {
        return chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(chatMemory, DEFAULT_CONVERSATION_ID, CHAT_HISTORY_WINDOW_SIZE),
                        new SimpleLoggerAdvisor())
                .build();
    }
}
