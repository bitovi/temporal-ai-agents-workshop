package demo;

import demo.common.GenericLLMProvider;
import demo.common.tools.WeatherTool;
import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.models.chat.OllamaChatMessageRole;
import io.github.ollama4j.models.chat.OllamaChatRequest;
import io.github.ollama4j.models.chat.OllamaChatRequestBuilder;
import io.github.ollama4j.models.chat.OllamaChatResult;
import io.github.ollama4j.tools.Tools;

public class RepkaStandalone {
    public static void main(String[] args) throws Exception {

        System.out.println("Starting RepkaStandalone...");

        System.out.println("Creating Ollama Instance...");
        OllamaAPI ollamaAPI = GenericLLMProvider.getOllamaInstance();

        System.out.println("Selecting Model...");
        String modelName = GenericLLMProvider.getOllamaModel();
        OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance(modelName);

        System.out.println("Registering Weather Tool...");
        final Tools.ToolSpecification weatherToolSpec = WeatherTool.getSpecification();

        ollamaAPI.registerTool(weatherToolSpec);

        System.out.println("Creating Chat Request...");
        OllamaChatRequest requestModel = builder
                .withMessage(OllamaChatMessageRole.USER,
                        "What is the weather in New York today?")
                .build();

        System.out.println("Sending Chat Request...");
        OllamaChatResult chatResult = ollamaAPI.chat(requestModel);

        System.out.println("Chat Result Received.");
        System.out.println("First answer: " + chatResult.getResponseModel().getMessage().getContent());
    }
}
