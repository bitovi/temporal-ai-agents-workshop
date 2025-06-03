package demo.common;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import demo.common.tools.WeatherTool;
import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.exceptions.OllamaBaseException;
import io.github.ollama4j.exceptions.ToolInvocationException;
import io.github.ollama4j.models.chat.OllamaChatMessage;
import io.github.ollama4j.models.chat.OllamaChatResult;
import io.github.ollama4j.models.embeddings.OllamaEmbedResponseModel;
import io.github.ollama4j.models.response.OllamaResult;
import io.github.ollama4j.tools.Tools;

public class GenericLLMProvider {
    public static String completion(String prompt) throws LLMProviderException {
        OllamaAPI ollamaAPI = getOllamaInstance();
        String model = getOllamaModel();

        OllamaResult result;
        try {
            // Generate a greeting using the Ollama API
            result = ollamaAPI.generate(model, prompt, null);
        } catch (OllamaBaseException | IOException | InterruptedException e) {
            throw new LLMProviderException(e.getMessage());
        }

        String response = result.getResponse();
        return response;
    }

    public static String chat(ArrayList<OllamaChatMessage> prompt) throws LLMProviderException {
        OllamaAPI ollamaAPI = getOllamaInstance();

        final Tools.ToolSpecification weatherToolSpecification = WeatherTool.getSpecification();
        ollamaAPI.registerTool(weatherToolSpecification);

        OllamaChatResult result;
        try {
            // Generate a greeting using the Ollama API
            result = ollamaAPI.chat("phi4-mini", prompt);
        } catch (OllamaBaseException | IOException | InterruptedException | ToolInvocationException e) {
            throw new LLMProviderException(e.getMessage());
        }

        return result.getResponseModel().getMessage().getContent();
    }

    public static List<List<Double>> generateEmbeddings(String text) throws LLMProviderException {
        OllamaAPI ollamaAPI = getOllamaInstance();
        String model = getOllamaEmbeddingModel();

        OllamaEmbedResponseModel result;
        try {
            // Generate a greeting using the Ollama API
            result = ollamaAPI.embed(model, Arrays.asList(text));
        } catch (OllamaBaseException | IOException | InterruptedException e) {
            throw new LLMProviderException(e.getMessage());
        }

        List<List<Double>> embeddings = result.getEmbeddings();
        return embeddings;
    }

    public static OllamaAPI getOllamaInstance() {
        OllamaAPI ollama = new OllamaAPI("http://fractal.local.repkam09.com:11434/");
        ollama.setVerbose(true);
        ollama.setRequestTimeoutSeconds(120);
        return ollama;
    }

    public static String getOllamaModel() {
        return "mistral:latest";
    }

    public static String getOllamaEmbeddingModel() {
        return "nomic-embed-text:latest";
    }
}
