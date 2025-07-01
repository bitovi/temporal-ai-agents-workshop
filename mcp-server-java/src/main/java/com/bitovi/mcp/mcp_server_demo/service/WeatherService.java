package com.bitovi.mcp.mcp_server_demo.service;

import java.util.List;
import java.util.Random;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonProperty;

public class WeatherService implements Function<WeatherService.Request, WeatherService.Response> {

    private static final List<String> WEATHER_CONDITIONS = List.of(
            "Sunny", "Partly Cloudy", "Cloudy", "Rainy", "Thunderstorms", "Snowy", "Foggy", "Windy");

    private final Random random = new Random();

    public record Request(@JsonProperty(required = true, value = "zipCode") String zipCode) {
    }

    public record DailyForecast(String date, String condition, int highTemp, int lowTemp) {
    }

    public record Response(String location, String currentCondition, int currentTemp, List<DailyForecast> forecast) {
    }

    @Override
    public Response apply(Request request) {
        // For demo purposes, generate a random weather forecast based on zip code
        String location = "Location for " + request.zipCode();
        String currentCondition = WEATHER_CONDITIONS.get(random.nextInt(WEATHER_CONDITIONS.size()));
        int currentTemp = 60 + random.nextInt(40); // Random temp between 60-100F

        // Generate a 5-day forecast
        List<DailyForecast> forecast = List.of(
                createForecast("2025-07-02"),
                createForecast("2025-07-03"),
                createForecast("2025-07-04"),
                createForecast("2025-07-05"),
                createForecast("2025-07-06"));

        return new Response(location, currentCondition, currentTemp, forecast);
    }

    private DailyForecast createForecast(String date) {
        String condition = WEATHER_CONDITIONS.get(random.nextInt(WEATHER_CONDITIONS.size()));
        int highTemp = 65 + random.nextInt(35); // Random high temp between 65-100F
        int lowTemp = 45 + random.nextInt(20); // Random low temp between 45-65F
        return new DailyForecast(date, condition, highTemp, lowTemp);
    }
}
