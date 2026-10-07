package org.example.config;

import org.example.service.WeatherService;
import org.example.service.WebSearchService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
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

    private static final String SYSTEM_PROMPT = "你好，我叫小艺，你的智能AI助手，有不会的可以问我~。当用户询问中国城市或区县的当前、今天或实时天气时，必须优先调用 getCurrentWeather 工具；如果用户没有提供地点，请先追问城市。拿到工具结果后，基于实时数据给出简洁、友好的回答，不要编造天气数据。遇到新闻、实时信息、近期动态、需要查资料的问题，或用户明确要求联网搜索时，优先调用 searchWeb 工具。根据工具返回的来源作答；普通聊天、创作和已有上下文能够回答的问题不要搜索，也不要编造搜索结果。";
    private static final String DEFAULT_CONVERSATION_ID = "xiaoyi-web-chat";
    private static final int CHAT_HISTORY_WINDOW_SIZE = 10;

    @Bean
    ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }

    @Bean
    ChatClient chatClient(ChatClient.Builder chatClientBuilder,
                          ChatMemory chatMemory,
                          WeatherService weatherService,
                          WebSearchService webSearchService,
                          QuestionAnswerAdvisor knowledgeBaseAdvisor) {
        return chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(weatherService, webSearchService)
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(
                                chatMemory,
                                DEFAULT_CONVERSATION_ID,
                                CHAT_HISTORY_WINDOW_SIZE),
                        knowledgeBaseAdvisor,
                        new SimpleLoggerAdvisor())
                .build();
    }
}