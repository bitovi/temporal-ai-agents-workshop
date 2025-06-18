package bitovi.providers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import bitovi.common.Config;
import bitovi.common.DataTypes;
import bitovi.common.LLMProviderException;
import bitovi.common.ModelContextProtocolClient;
import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.exceptions.OllamaBaseException;
import io.github.ollama4j.models.chat.OllamaChatMessage;
import io.github.ollama4j.models.chat.OllamaChatMessageRole;
import io.github.ollama4j.models.chat.OllamaChatResult;
import io.github.ollama4j.models.embeddings.OllamaEmbedResponseModel;
import io.github.ollama4j.models.response.Model;
import io.github.ollama4j.tools.ToolFunction;
import io.github.ollama4j.tools.Tools;
import io.github.ollama4j.tools.Tools.PromptFuncDefinition.Parameters.ParametersBuilder;
import io.github.ollama4j.tools.Tools.PromptFuncDefinition.Property;
import io.github.ollama4j.tools.Tools.PromptFuncDefinition.Property.PropertyBuilder;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;

public class OllamaProvider implements BaseModelProvider {

    protected final String OLLAMA_MODEL_ID;
    protected final String OLLAMA_HOST;

    private OllamaAPI ollamaClient;

    public OllamaProvider() {
        this.OLLAMA_MODEL_ID = Config.getProperty("OLLAMA_MODEL_ID");
        this.OLLAMA_HOST = Config.getProperty("OLLAMA_HOST");

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
    public DataTypes.MessageRecord chat(ArrayList<DataTypes.MessageRecord> prompt) throws LLMProviderException {
        try {
            OllamaChatResult response = this.ollamaClient.chat(OLLAMA_MODEL_ID, this.convertCommonToOllama(prompt));
            var lastMessage = response.getResponseModel().getMessage();
            return this.convertOllamaToCommon(lastMessage);
        } catch (Exception e) {
            System.err.printf("ERROR: Can't invoke '%s'. Reason: %s", OLLAMA_MODEL_ID, e.getMessage());
            throw new LLMProviderException(e.getMessage());
        }
    }

    @Override
    public List<Float> embedding(String input) throws LLMProviderException {
        OllamaEmbedResponseModel result;
        try {
            result = this.ollamaClient.embed(OLLAMA_MODEL_ID, List.of(input));
        } catch (OllamaBaseException | IOException | InterruptedException e) {
            throw new LLMProviderException(e.getMessage());
        }

        List<List<Double>> embeddings = result.getEmbeddings();
        return embeddings.isEmpty() ? List.of() : embeddings.get(0).stream().map(Double::floatValue).toList();
    }

    private List<OllamaChatMessage> convertCommonToOllama(ArrayList<DataTypes.MessageRecord> prompt) {
        ArrayList<OllamaChatMessage> ollamaMessages = new ArrayList<>();
        for (DataTypes.MessageRecord message : prompt) {
            OllamaChatMessage ollamaMessage = new OllamaChatMessage();

            switch (message.role()) {
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

            ollamaMessage.setContent(message.content());
            ollamaMessages.add(ollamaMessage);
        }

        return ollamaMessages;
    }

    private DataTypes.MessageRecord convertOllamaToCommon(OllamaChatMessage ollamaMessage) {
        return new DataTypes.MessageRecord(ollamaMessage.getRole().toString(), ollamaMessage.getContent());
    }

    public static Tools.ToolSpecification transform(io.modelcontextprotocol.spec.McpSchema.Tool tool,
            ModelContextProtocolClient mcpToolIntegration) {
        JsonSchema js = tool.inputSchema();

        Map<String, Object> properties = js.properties();
        if (properties == null || properties.isEmpty()) {
            throw new IllegalArgumentException("Tool must have at least one property in the input schema");
        }

        Map<String, Property> ollamaProperties = new java.util.HashMap<>();

        properties.forEach((propertyName, propertyValue) -> {
            if (propertyValue instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> propertyMap = (Map<String, Object>) propertyValue;
                PropertyBuilder propertyBuilder = Property.builder()
                        .type((String) propertyMap.get("type"))
                        .description((String) propertyMap.get("description"));
                ollamaProperties.put(propertyName, propertyBuilder.build());
            }
        });

        ParametersBuilder pb = Tools.PromptFuncDefinition.Parameters.builder()
                .type("object")
                .properties(ollamaProperties)
                .required(tool.inputSchema().required());

        return Tools.ToolSpecification.builder()
                .functionName(tool.name())
                .functionDescription(tool.description())
                .toolFunction(buildToolHandler(tool.name(), mcpToolIntegration))
                .toolPrompt(
                        Tools.PromptFuncDefinition.builder()
                                .type("prompt")
                                .function(
                                        Tools.PromptFuncDefinition.PromptFuncSpec
                                                .builder()
                                                .name(tool.name())
                                                .description(tool
                                                        .description())
                                                .parameters(pb.build())
                                                .build())
                                .build())
                .build();
    }

    public static ToolFunction buildToolHandler(String toolName, ModelContextProtocolClient mcpToolIntegration) {
        return (args) -> {
            try {
                return mcpToolIntegration.executeMCPTool(
                        toolName,
                        args);
            } catch (Exception e) {
                System.out.println(
                        "Failed to execute MCP tool " + toolName + ": " + e.getMessage());
                return null;
            }
        };
    }
}
