package org.example.controller;

import org.example.dto.WeatherResponse;
import org.example.service.WeatherService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the current-weather query endpoint.
 */
@RestController
@RequestMapping("/weather")
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping("/current")
    public WeatherResponse getCurrentWeather(@RequestParam String city) {
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("城市不能为空");
        }
        if (city.length() > 80) {
            throw new IllegalArgumentException("城市名称过长");
        }
        return weatherService.getCurrentWeather(city.trim());
    }
}
