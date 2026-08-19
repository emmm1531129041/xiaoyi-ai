package org.example.dto;

/**
 * The current weather returned by the AMap Web Service API.
 */
public record WeatherResponse(
        String city,
        String weather,
        String temperature,
        String windDirection,
        String windPower,
        String humidity,
        String reportTime) {
}
