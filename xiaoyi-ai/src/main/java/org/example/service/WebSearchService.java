package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.dto.WebSearchResult;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Uses Tavily Search to provide current web information to the chat model.
 */
@Service
public class WebSearchService {

    private static final String TAVILY_SEARCH_URL = "https://api.tavily.com/search";

    private final RestClient restClient;
    private final String apiKey;

    public WebSearchService(@Value("${xiaoyi.search.tavily.api-key:}") String apiKey) {
        this.restClient = RestClient.create();
        this.apiKey = apiKey;
    }

    @Tool(name = "searchWeb", description = "联网搜索最新公开资料、新闻、实时动态或用户明确要求查询的信息。query 应为精炼的搜索关键词。不要用于普通闲聊、写作或可以直接回答的稳定常识。")
    public List<WebSearchResult> searchWeb(@ToolParam(description = "用户问题对应的精炼搜索关键词") String query) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("未配置 Tavily API Key，请设置 TAVILY_API_KEY 环境变量");
        }

        JsonNode response = restClient.post()
                .uri(TAVILY_SEARCH_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "query", query,
                        "search_depth", "basic",
                        "max_results", 5,
                        "include_answer", false,
                        "include_raw_content", false))
                .retrieve()
                .body(JsonNode.class);

        if (response == null || !response.path("results").isArray()) {
            throw new IllegalStateException("联网搜索服务没有返回有效结果");
        }

        List<WebSearchResult> results = new ArrayList<>();
        for (JsonNode item : response.path("results")) {
            results.add(new WebSearchResult(
                    item.path("title").asText(),
                    item.path("url").asText(),
                    item.path("content").asText()));
        }
        return results;
    }
}
