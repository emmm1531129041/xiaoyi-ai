package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.dto.WeatherResponse;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Queries current weather from AMap's Web Service API.
 */
@Service
public class WeatherService {

    private static final String AMAP_HOST = "https://restapi.amap.com";

    private final RestClient restClient;
    private final String apiKey;

    public WeatherService(@Value("${xiaoyi.weather.amap.api-key}") String apiKey) {
        this.restClient = RestClient.create(AMAP_HOST);
        this.apiKey = apiKey;
    }

    @Tool(name = "getCurrentWeather", description = "查询中国城市或区县的实时天气。仅在用户询问当前、今天或实时天气时调用；city 必须是用户提供的城市或区县名称，例如杭州、北京市朝阳区。")
    public WeatherResponse getCurrentWeather(String city) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("未配置高德 Web 服务 API Key");
        }

        String adcode = resolveAdcode(city);
        JsonNode weatherResult = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v3/weather/weatherInfo")
                        .queryParam("key", apiKey)
                        .queryParam("city", adcode)
                        .queryParam("extensions", "base")
                        .build())
                .retrieve()
                .body(JsonNode.class);
        ensureSuccess(weatherResult, "天气查询");

        JsonNode live = firstItem(weatherResult, "lives", "未查询到该城市的实况天气");
        return new WeatherResponse(
                live.path("city").asText(city),
                live.path("weather").asText(),
                live.path("temperature").asText(),
                live.path("winddirection").asText(),
                live.path("windpower").asText(),
                live.path("humidity").asText(),
                live.path("reporttime").asText());
    }

    private String resolveAdcode(String city) {
        JsonNode geocodeResult = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v3/geocode/geo")
                        .queryParam("key", apiKey)
                        .queryParam("address", city)
                        .build())
                .retrieve()
                .body(JsonNode.class);
        ensureSuccess(geocodeResult, "城市查询");

        JsonNode geocode = firstItem(geocodeResult, "geocodes", "未找到该城市");
        String adcode = geocode.path("adcode").asText();
        if (adcode.isBlank()) {
            throw new IllegalStateException("未找到该城市的行政区编码");
        }
        return adcode;
    }

    private void ensureSuccess(JsonNode result, String operation) {
        if (result == null || !"1".equals(result.path("status").asText())) {
            String message = result == null ? "服务没有返回数据" : result.path("info").asText("未知错误");
            throw new IllegalStateException(operation + "失败：" + message);
        }
    }

    private JsonNode firstItem(JsonNode result, String field, String emptyMessage) {
        JsonNode items = result.path(field);
        if (!items.isArray() || items.isEmpty()) {
            throw new IllegalStateException(emptyMessage);
        }
        return items.get(0);
    }
}
