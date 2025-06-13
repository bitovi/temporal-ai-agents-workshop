package bitovi.demos;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import bitovi.Config;
import bitovi.providers.LLMProviderException;
import bitovi.providers.MCPToolIntegration;
import bitovi.providers.OllamaProvider;
import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.exceptions.OllamaBaseException;
import io.github.ollama4j.exceptions.ToolInvocationException;
import io.github.ollama4j.models.chat.OllamaChatMessage;
import io.github.ollama4j.models.chat.OllamaChatMessageRole;
import io.github.ollama4j.models.chat.OllamaChatResult;

public class BitoviOllamaToolCall {

    private static MCPToolIntegration mcpToolIntegration;
    private static OllamaAPI ollamaClient;
    private static String OLLAMA_MODEL_ID;
    private static String OLLAMA_HOST;

    public static void main(String[] args) throws LLMProviderException, OllamaBaseException, IOException,
            InterruptedException, ToolInvocationException {
        OLLAMA_MODEL_ID = Config.getProperty("OLLAMA_MODEL_ID");
        OLLAMA_HOST = Config.getProperty("OLLAMA_HOST");

        ollamaClient = new OllamaAPI(OLLAMA_HOST);
        ollamaClient.setVerbose(true);
        ollamaClient.setRequestTimeoutSeconds(120);
        List<OllamaChatMessage> messages = new ArrayList<OllamaChatMessage>();

        OllamaChatMessage ollamaMessage = new OllamaChatMessage();
        ollamaMessage.setRole(OllamaChatMessageRole.USER);
        // ollamaMessage.setContent(
        // "Can you tell me what tool calls you have available and what they do, what
        // their parameters are, and what they might be used for?");

        ollamaMessage.setContent(
                "Can you tell me the weather right now in Rush, NY? The zip code is 14543.");

        messages.add(ollamaMessage);

        mcpToolIntegration = new MCPToolIntegration();
        mcpToolIntegration.getAvailableTools().forEach(tool -> {
            ollamaClient.registerTool(OllamaProvider.transform(tool, mcpToolIntegration));
        });

        // ollamaClient.registerTool(CosineToolImpl.getOllamaTool());

        OllamaChatResult response = ollamaClient.chat(OLLAMA_MODEL_ID, messages);
        OllamaChatMessage lastMessage = response.getResponseModel().getMessage();

        // Print the final response message
        System.out.println("Final Response: " + lastMessage.getContent());
    }
}
