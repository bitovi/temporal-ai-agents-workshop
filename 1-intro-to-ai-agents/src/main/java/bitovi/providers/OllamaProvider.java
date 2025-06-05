package bitovi.providers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.exceptions.OllamaBaseException;
import io.github.ollama4j.models.chat.OllamaChatMessage;
import io.github.ollama4j.models.chat.OllamaChatMessageRole;
import io.github.ollama4j.models.chat.OllamaChatResult;
import io.github.ollama4j.models.embeddings.OllamaEmbedResponseModel;
import io.github.ollama4j.models.response.Model;
import io.github.ollama4j.models.response.OllamaResult;

public class OllamaProvider implements LLMProvider {

    private final static String MODEL_ID = "mistral:latest";
    private final static String OLLAMA_HOST = "http://fractal.local.repkam09.com:11434/";

    private OllamaAPI ollamaClient;

    public OllamaProvider() {
        this.ollamaClient = new OllamaAPI(OLLAMA_HOST);
        this.ollamaClient.setVerbose(true);
        this.ollamaClient.setRequestTimeoutSeconds(120);
    }

    public OllamaAPI getOllamaClient() {
        return this.ollamaClient;
    }

    @Override
    public ArrayList<String> getModels() throws LLMProviderException {
        ArrayList<String> modelNames = new ArrayList<>();
        try {
            List<Model> models = this.ollamaClient.listModels();
            if (models.isEmpty()) {
                System.out.println("No available models in Ollama.");
            } else {
                for (Model model : models) {
                    modelNames.add(model.getName());
                }
            }

            return modelNames;
        } catch (Exception e) {
            System.err.println(e.getMessage());
            throw new LLMProviderException(e.getMessage());
        }
    }

    @Override
    public String completion(String prompt) throws LLMProviderException {
        try {
            OllamaResult response = this.ollamaClient.generate(MODEL_ID, prompt, null);
            return response.getResponse();
        } catch (Exception e) {
            System.err.printf("ERROR: Can't invoke '%s'. Reason: %s", MODEL_ID, e.getMessage());
            throw new LLMProviderException(e.getMessage());
        }
    }

    @Override
    public LLMProviderChatMessage chat(ArrayList<LLMProviderChatMessage> prompt) throws LLMProviderException {
        try {
            OllamaChatResult response = this.ollamaClient.chat(MODEL_ID, this.convertCommonToOllama(prompt));
            var lastMessage = response.getResponseModel().getMessage();
            return this.convertOllamaToCommon(lastMessage);
        } catch (Exception e) {
            System.err.printf("ERROR: Can't invoke '%s'. Reason: %s", MODEL_ID, e.getMessage());
            throw new LLMProviderException(e.getMessage());
        }
    }

    @Override
    public List<List<Double>> embedding(List<String> inputs) throws LLMProviderException {
        OllamaEmbedResponseModel result;
        try {
            // Generate a greeting using the Ollama API
            result = this.ollamaClient.embed(MODEL_ID, inputs);
        } catch (OllamaBaseException | IOException | InterruptedException e) {
            throw new LLMProviderException(e.getMessage());
        }

        List<List<Double>> embeddings = result.getEmbeddings();
        return embeddings;
    }

    private List<OllamaChatMessage> convertCommonToOllama(ArrayList<LLMProviderChatMessage> prompt) {
        ArrayList<OllamaChatMessage> ollamaMessages = new ArrayList<>();
        for (LLMProviderChatMessage message : prompt) {
            OllamaChatMessage ollamaMessage = new OllamaChatMessage();

            switch (message.getRole()) {
                case "user":
                    ollamaMessage.setRole(OllamaChatMessageRole.USER);
                    break;
                case "assistant":
                    ollamaMessage.setRole(OllamaChatMessageRole.ASSISTANT);
                    break;
                case "system":
                    ollamaMessage.setRole(OllamaChatMessageRole.SYSTEM);
                    break;
                default:
                    ollamaMessage.setRole(OllamaChatMessageRole.USER); // Default to USER if role is unknown
            }

            ollamaMessage.setContent(message.getContent());
            ollamaMessages.add(ollamaMessage);
        }

        return ollamaMessages;
    }

    private LLMProviderChatMessage convertOllamaToCommon(OllamaChatMessage ollamaMessage) {
        return new LLMProviderChatMessage(ollamaMessage.getRole().toString(), ollamaMessage.getContent());
    }
}
