package bitovi.providers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import bitovi.Config;
import bitovi.common.tools.ConsineTool.CosineToolImpl;
import bitovi.records.MessageRecord;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrock.BedrockClient;
import software.amazon.awssdk.services.bedrock.model.FoundationModelSummary;
import software.amazon.awssdk.services.bedrock.model.ListFoundationModelsResponse;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

public class BedrockProvider implements LLMProvider {

    private String AWS_MODEL_ID;
    private String AWS_MODEL_ARN;

    private BedrockClient bedrockClient;
    private BedrockRuntimeClient bedrockRuntimeClient;
    private final Region region = Region.US_EAST_2; // Default region, can be changed as needed

    // MCP Tool Integration
    private MCPToolIntegration mcpIntegration;

    public BedrockProvider() {
        this.AWS_MODEL_ID = Config.getProperty("AWS_MODEL_ID");
        this.AWS_MODEL_ARN = Config.getProperty("AWS_MODEL_ARN");

        String AWS_ACCESS_KEY_ID = Config.getProperty("AWS_ACCESS_KEY_ID");
        String AWS_SECRET_ACCESS_KEY = Config.getProperty("AWS_SECRET_ACCESS_KEY");
        String AWS_SESSION_TOKEN = Config.getProperty("AWS_SESSION_TOKEN");

        this.bedrockClient = BedrockClient.builder()
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsSessionCredentials.create(
                                        AWS_ACCESS_KEY_ID,
                                        AWS_SECRET_ACCESS_KEY,
                                        AWS_SESSION_TOKEN)))
                .region(region)
                .build();

        this.bedrockRuntimeClient = BedrockRuntimeClient.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsSessionCredentials.create(
                                AWS_ACCESS_KEY_ID,
                                AWS_SECRET_ACCESS_KEY,
                                AWS_SESSION_TOKEN)))
                .region(region)
                .build();

        // Initialize MCP tool integration
        try {
            this.mcpIntegration = new MCPToolIntegration();
        } catch (Exception e) {
            System.err.println("Failed to initialize MCP integration: " + e.getMessage());
            this.mcpIntegration = null;
        }
    }

    @Override
    public ArrayList<String> getModels() throws LLMProviderException {
        ArrayList<String> modelNames = new ArrayList<>();
        try {
            ListFoundationModelsResponse response = bedrockClient.listFoundationModels(r -> {
            });

            List<FoundationModelSummary> models = response.modelSummaries();

            if (models.isEmpty()) {
                System.out.println("No available models in " + region.toString());
            } else {
                for (FoundationModelSummary model : models) {
                    modelNames.add(model.modelId());
                }
            }

            return modelNames;

        } catch (SdkClientException e) {
            System.err.println(e.getMessage());
            throw new LLMProviderException(e.getMessage());
        }
    }

    @Override
    public String completion(String prompt) throws LLMProviderException {
        try {
            JSONObject jsonBody = new JSONObject()
                    .put("prompt", prompt)
                    .put("temperature", 0.5F);

            InvokeModelResponse invokeResponse = this.bedrockRuntimeClient.invokeModel(InvokeModelRequest.builder()
                    .modelId(AWS_MODEL_ARN)
                    .body(SdkBytes.fromUtf8String(jsonBody.toString()))
                    .build());

            String utf8 = invokeResponse.body().asUtf8String();
            String completion = new JSONObject(utf8)
                    .getString("generation");

            return completion;
        } catch (SdkClientException e) {
            System.err.printf("ERROR: Can't invoke '%s'. Reason: %s", AWS_MODEL_ID, e.getMessage());
            throw new LLMProviderException(e.getMessage());
        }
    }

    @Override
    public MessageRecord chat(ArrayList<MessageRecord> prompt) throws LLMProviderException {

        // TODO: Implement chat functionality for BedrockProvider instead of faking it
        // with completion.

        // Convert the chat messages to a single prompt string
        StringBuilder promptBuilder = new StringBuilder();
        for (MessageRecord message : prompt) {
            promptBuilder.append(message.role()).append(": ").append(message.content()).append("\n\n");
        }
        String completion = this.completion(promptBuilder.toString());

        // Create a new chat message with the model's response
        MessageRecord responseMessage = new MessageRecord("assistant", completion);
        return responseMessage;
    }

    public MessageRecord chatWithTools(ArrayList<MessageRecord> prompt,
            List<Tool> tools) throws LLMProviderException {

        List<Message> messages = new ArrayList<Message>();

        // Convert the chat messages to Bedrock's Message format
        for (MessageRecord message : prompt) {
            messages.add(Message.builder()
                    .role(ConversationRole.fromValue(message.role()))
                    .content(ContentBlock.fromText(message.content()))
                    .build());
        }

        ToolConfiguration.Builder toolConfig = ToolConfiguration.builder()
                .tools(CosineToolImpl.getBedrockToolSpecification());

        // Add MCP tools if integration is available
        if (mcpIntegration != null) {
            List<Tool> mcpTools = mcpIntegration.getBedrockToolSpecifications();
            if (mcpTools != null && !mcpTools.isEmpty()) {
                // Add MCP tools to the tool configuration
                toolConfig.tools(mcpTools);
            }
        }

        ConverseRequest request = ConverseRequest.builder()
                .modelId(AWS_MODEL_ARN)
                .messages(messages)
                .toolConfig(toolConfig.build())
                .build();

        ConverseResponse response = converseWithToolsRecursive(messages, request, 0);
        if (response == null || response.output() == null || response.output().message() == null) {
            throw new LLMProviderException("No response received from Bedrock.");
        }

        // Convert the Bedrock response back to MessageRecord format
        MessageRecord responseMessage = new MessageRecord(
                response.output().message().role().toString(),
                response.output().message().content().get(0).text());

        return responseMessage;
    }

    private ConverseResponse converseWithToolsRecursive(List<Message> messages, ConverseRequest request, Integer depth)
            throws LLMProviderException {
        if (depth > 4) {
            throw new LLMProviderException("Exceeded maximum recursion depth for tool invocation.");
        }

        ConverseResponse response = bedrockRuntimeClient.converse(request);

        try {
            for (ContentBlock block : response.output().message().content()) {
                if (block.toolUse() != null) {
                    String result;

                    ToolUseBlock toolUseBlock = block.toolUse();
                    switch (toolUseBlock.name()) {
                        // One simple hardcoded tool for testing
                        case "calculate_cosine": {
                            double number = toolUseBlock.input().asMap().get("number").asNumber().doubleValue();
                            result = String.valueOf(CosineToolImpl.calculateCosine(number));
                            break;
                        }

                        default: {
                            // Try to execute as MCP tool
                            if (mcpIntegration != null) {
                                try {
                                    Map<String, Object> objectMap = new java.util.HashMap<>();
                                    Map<String, Document> docMap = toolUseBlock
                                            .input().asMap();
                                    for (Map.Entry<String, Document> entry : docMap
                                            .entrySet()) {
                                        objectMap.put(entry.getKey(), entry.getValue().toString());
                                    }
                                    result = mcpIntegration.executeMCPTool(
                                            toolUseBlock.name(),
                                            objectMap);
                                } catch (Exception e) {
                                    result = "Error executing MCP tool: " + e.getMessage();
                                }
                            } else {
                                throw new LLMProviderException("Unknown tool used: " + toolUseBlock.name());
                            }
                            break;
                        }
                    }

                    // Add the tool result to the messages
                    messages.add(response.output().message());

                    ToolResultBlock toolResultBlock = ToolResultBlock.builder()
                            .toolUseId(toolUseBlock.toolUseId())
                            .content(ToolResultContentBlock.builder().text(result).build())
                            .build();

                    messages.add(Message.builder()
                            .role(ConversationRole.USER)
                            .content(ContentBlock.fromToolResult(toolResultBlock))
                            .build());

                    // Create a new ConverseRequest with the updated messages
                    ConverseRequest newRequest = ConverseRequest.builder()
                            .modelId(request.modelId())
                            .messages(messages)
                            .toolConfig(request.toolConfig())
                            .build();

                    // Recursively call converseWithToolsRecursive with the updated messages
                    return converseWithToolsRecursive(messages, newRequest, depth + 1);
                }

                messages.add(response.output().message());
                return response; // No tool use, return the response
            }
        } catch (Exception e) {
            throw new LLMProviderException("Error during conversation: " + e.getMessage());
        }

        // If no tool use was found, return the response
        return response;
    }

    @Override
    public List<List<Double>> embedding(List<String> inputs) throws LLMProviderException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'embedding'");
    }

    /**
     * Enhanced chat method that supports both local and MCP tools
     */
    public MessageRecord chatWithAllTools(ArrayList<MessageRecord> prompt) throws LLMProviderException {
        List<Tool> allTools = new ArrayList<>();

        // Add local tools
        allTools.add(CosineToolImpl.getBedrockToolSpecification());

        // Add MCP tools if integration is available
        if (mcpIntegration != null) {
            allTools.addAll(mcpIntegration.getBedrockToolSpecifications());
        }

        return chatWithTools(prompt, allTools);
    }
}
