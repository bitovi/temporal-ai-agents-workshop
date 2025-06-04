package bitovi.common;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import bitovi.common.tools.WeatherTool;
import org.json.JSONObject;
import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.exceptions.OllamaBaseException;
import io.github.ollama4j.exceptions.ToolInvocationException;
import io.github.ollama4j.models.chat.OllamaChatMessage;
import io.github.ollama4j.models.chat.OllamaChatResult;
import io.github.ollama4j.models.embeddings.OllamaEmbedResponseModel;
import io.github.ollama4j.models.response.OllamaResult;
import io.github.ollama4j.tools.Tools;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrock.BedrockClient;
import software.amazon.awssdk.services.bedrock.model.FoundationModelSummary;
import software.amazon.awssdk.services.bedrock.model.ListFoundationModelsResponse;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

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

    public static List<FoundationModelSummary> listFoundationModels(BedrockClient bedrockClient, Region region) {

        try {
            ListFoundationModelsResponse response = bedrockClient.listFoundationModels(r -> {
            });

            List<FoundationModelSummary> models = response.modelSummaries();

            if (models.isEmpty()) {
                System.out.println("No available foundation models in " + region.toString());
            } else {
                for (FoundationModelSummary model : models) {
                    System.out.println("Model ID: " + model.modelId());
                    System.out.println("Provider: " + model.providerName());
                    System.out.println("Name:     " + model.modelName());
                    System.out.println();
                }
            }

            return models;

        } catch (SdkClientException e) {
            System.err.println(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public static String converse(BedrockRuntimeClient client, String inputText) {
        // Set the model ID, e.g., Llama 3 8b Instruct.
        var modelId = "meta.llama3-1-8b-instruct-v1:0";

        try {
            JSONObject jsonBody = new JSONObject()
                    .put("prompt", inputText)
                    .put("temperature", 0.5F);

            InvokeModelResponse invokeResponse = client.invokeModel(InvokeModelRequest.builder()
                    .modelId(
                            "arn:aws:bedrock:us-east-2:755521597925:inference-profile/us.meta.llama3-1-8b-instruct-v1:0")
                    .body(SdkBytes.fromUtf8String(jsonBody.toString()))
                    .build());

            String utf8 = invokeResponse.body().asUtf8String();
            String completion = new JSONObject(utf8)
                    .getString("generation");

            System.out.println("Response: " + completion);
            return completion;

        } catch (SdkClientException e) {
            System.err.printf("ERROR: Can't invoke '%s'. Reason: %s", modelId, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public static String getOllamaModel() {
        return "mistral:latest";
    }

    public static String getOllamaEmbeddingModel() {
        return "nomic-embed-text:latest";
    }
}
