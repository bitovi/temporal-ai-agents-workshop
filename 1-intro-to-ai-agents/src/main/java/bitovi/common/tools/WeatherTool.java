package bitovi.common.tools;

import java.util.Map;

import io.github.ollama4j.tools.Tools;
import io.github.ollama4j.tools.Tools.PromptFuncDefinition;

public class WeatherTool {
        public static Tools.ToolSpecification getSpecification() {
                PromptFuncDefinition tool = new PromptFuncDefinition();
                return Tools.ToolSpecification.builder()
                                .functionName("get-location-weather-info")
                                .functionDescription("Get current weather for a city by its name")
                                .toolFunction(WeatherTool::getCurrentWeather)
                                .toolPrompt(tool)
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