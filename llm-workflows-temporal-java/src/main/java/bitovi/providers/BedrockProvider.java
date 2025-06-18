package bitovi.providers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bitovi.common.Config;
import bitovi.common.LLMProviderException;
import bitovi.common.ModelContextProtocolClient;
import bitovi.common.DataTypes.MessageRecord;
import bitovi.common.tools.CosineToolImpl;
import bitovi.common.tools.SearchToolmpl;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
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
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

public class BedrockProvider implements BaseModelProvider {

    protected String AWS_MODEL_ID;
    protected String AWS_MODEL_ARN;

    private BedrockClient bedrockClient;
    private BedrockRuntimeClient bedrockRuntimeClient;
    private final Region region = Region.US_EAST_2; // Default region, can be changed as needed

    // MCP Tool Integration
    private ModelContextProtocolClient mcpIntegration;

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
            this.mcpIntegration = new ModelContextProtocolClient();
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
    public MessageRecord chat(ArrayList<MessageRecord> prompt) throws LLMProviderException {
        List<Message> messages = new ArrayList<Message>();

        // Convert the chat messages to Bedrock's Message format
        for (MessageRecord message : prompt) {
            messages.add(Message.builder()
                    .role(ConversationRole.fromValue(message.role()))
                    .content(ContentBlock.fromText(message.content()))
                    .build());
        }

        ToolConfiguration.Builder toolConfig = ToolConfiguration.builder();

        List<Tool> tools = new ArrayList<>();
        // Add the cosine tool to the tool configuration
        Tool cosineTool = CosineToolImpl.getBedrockTool();
        tools.add(cosineTool);

        // Add the search tool to the tool configuration
        Tool searchTool = SearchToolmpl.getBedrockTool();
        tools.add(searchTool);

        // Add MCP tools if integration is available
        if (mcpIntegration != null) {
            List<Tool> mcpTools = mcpIntegration.getBedrockToolSpecifications();
            tools.addAll(mcpTools);
        }

        System.out.println("Adding " + tools.size() + " tools to Bedrock tool configuration.");
        toolConfig.tools(tools);

        String systemPrompt = "You are a helpful assistant named 'Mark' that can perform various tasks including calculations and searching the web. Use these tools to best answer questions from the user.";

        ConverseRequest request = ConverseRequest.builder()
                .modelId(AWS_MODEL_ARN)
                .messages(messages)
                .toolConfig(toolConfig.build())
                .system(SystemContentBlock.fromText(systemPrompt))
                .build();

        ConverseResponse response = converseWithToolsRecursive(messages, request, 0);
        if (response == null || response.output() == null || response.output().message() == null) {
            throw new LLMProviderException("No response received from Bedrock.");
        }

        // Convert the Bedrock response back to MessageRecord format
        MessageRecord responseMessage = new MessageRecord(
                response.output().message().role().toString() != null ? response.output().message().role().toString()
                        : "assistant",
                response.output().message().content().get(0).text());

        return responseMessage;
    }

    private ConverseResponse converseWithToolsRecursive(List<Message> messages, ConverseRequest request, Integer depth)
            throws LLMProviderException {
        if (depth > 10) {
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
                            result = String.valueOf(CosineToolImpl.execute(toolUseBlock.input()));
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

    /*
     * Amazon Titan Embeddings G1 - Text Floating-point 1536
     * Amazon Titan Text Embeddings V2 Floating-point, binary 256, 512, 1024
     * Cohere Embed (English) Floating-point, binary 1024
     * Cohere Embed (Multilingual) Floating-point, binary 1024
     */
    @Override
    public List<List<Double>> embedding(List<String> inputs) throws LLMProviderException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'embedding'");
    }

    public static Tool transform(io.modelcontextprotocol.spec.McpSchema.Tool tool) {
        System.out.println("Transforming MCP Tool: " + tool.name());
        System.out.println("Description: " + tool.description());
        JsonSchema js = tool.inputSchema();

        Map<String, Document> propertiesMap = new HashMap<>();
        List<Document> requiredList = new ArrayList<>();

        Map<String, Object> properties = js.properties();
        if (properties == null || properties.isEmpty()) {
            throw new IllegalArgumentException("Tool must have at least one property in the input schema");
        }

        // Iterate over each property in the JSON Schema
        properties.forEach((propertyName, objectTypeDescription) -> {
            // Ensure the value is a Map<String, String> representing the JSON schema
            if (!(objectTypeDescription instanceof HashMap)) {
                throw new IllegalArgumentException("Expected value to be a HashMap<String, Document>");
            }

            @SuppressWarnings("unchecked")
            HashMap<String, String> valueSchema = (HashMap<String, String>) objectTypeDescription;

            // Convert the JSON schema to a Document
            Map<String, Document> propertyMap = new HashMap<>();

            if (valueSchema.containsKey("type")) {
                String type = valueSchema.get("type");
                propertyMap.put("type", Document.fromString(type));
            } else {
                throw new IllegalArgumentException("Each property must contain 'type'");
            }

            if (valueSchema.containsKey("description")) {
                propertyMap.put("description", Document.fromString(valueSchema.get("description")));
            }

            // Add the property to the properties map
            System.out.println("Property Found: " + propertyName + " with type: " + valueSchema.get("type"));
            propertiesMap.put(propertyName, Document.fromMap(propertyMap));
        });

        js.required().forEach(requiredField -> {
            // Ensure the required fields are added to the properties map
            if (propertiesMap.containsKey(requiredField)) {
                System.out.println("Required field: " + requiredField);
                requiredList.add(Document.fromString(requiredField));
            }
        });

        // Create the root object
        Map<String, Document> rootMap = new HashMap<>();
        rootMap.put("type", Document.fromString("object"));
        rootMap.put("properties", Document.fromMap(propertiesMap));
        rootMap.put("required", Document.fromList(requiredList));

        // Now create the Document representing the JSON schema
        Document document = Document.fromMap(rootMap);

        ToolSpecification specification = ToolSpecification.builder()
                .name(tool.name())
                .description(tool.description())
                .inputSchema(ToolInputSchema.builder()
                        .json(document)
                        .build())
                .build();

        return Tool.builder()
                .toolSpec(specification)
                .build();
    }

}
