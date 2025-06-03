package demo.common.tools;

import java.util.Map;

import io.github.ollama4j.tools.Tools;

public class WeatherTool {
        public static Tools.ToolSpecification getSpecification() {
                return Tools.ToolSpecification.builder()
                                .functionName("get-location-weather-info")
                                .functionDescription("Get current weather for a city by its name")
                                .toolFunction(WeatherTool::getCurrentWeather)
                                .toolPrompt(
                                                Tools.PromptFuncDefinition.builder()
                                                                .type("prompt")
                                                                .function(
                                                                                Tools.PromptFuncDefinition.PromptFuncSpec
                                                                                                .builder()
                                                                                                .name("get-location-weather-info")
                                                                                                .description("Get location details")
                                                                                                .parameters(
                                                                                                                Tools.PromptFuncDefinition.Parameters
                                                                                                                                .builder()
                                                                                                                                .type("object")
                                                                                                                                .properties(
                                                                                                                                                Map.of(
                                                                                                                                                                "city",
                                                                                                                                                                Tools.PromptFuncDefinition.Property
                                                                                                                                                                                .builder()
                                                                                                                                                                                .type("string")
                                                                                                                                                                                .description("The city, e.g. New Delhi, India or New York, USA.")
                                                                                                                                                                                .required(true)
                                                                                                                                                                                .build()))
                                                                                                                                .required(java.util.List
                                                                                                                                                .of("city"))
                                                                                                                                .build())
                                                                                                .build())
                                                                .build())
                                .build();
        }

        public static String getCurrentWeather(Map<String, Object> arguments) {
                System.out.println("WeatherTool called with arguments: " + arguments);
                // Get details from weather API
                String location = arguments.get("city").toString();
                return "Currently " + location
                                + "'s weather is Sunny with a tempreature of 74°F. It will be a high of 87°F and a low of 48°F.";
        }
}