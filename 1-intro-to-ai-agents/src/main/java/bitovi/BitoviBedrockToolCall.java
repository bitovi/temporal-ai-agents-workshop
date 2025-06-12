package bitovi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bitovi.common.tools.ConsineTool.CosineToolImpl;
import bitovi.common.tools.SearchTool.SearchToolmpl;
import bitovi.providers.LLMProviderException;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

public class BitoviBedrockToolCall {

    private static BedrockRuntimeClient bedrockRuntimeClient;

    public static void main(String[] args) throws LLMProviderException {
        String AWS_MODEL_ARN = Config.getProperty("AWS_MODEL_ARN");
        String AWS_ACCESS_KEY_ID = Config.getProperty("AWS_ACCESS_KEY_ID");
        String AWS_SECRET_ACCESS_KEY = Config.getProperty("AWS_SECRET_ACCESS_KEY");
        String AWS_SESSION_TOKEN = Config.getProperty("AWS_SESSION_TOKEN");

        bedrockRuntimeClient = BedrockRuntimeClient.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsSessionCredentials.create(
                                AWS_ACCESS_KEY_ID,
                                AWS_SECRET_ACCESS_KEY,
                                AWS_SESSION_TOKEN)))
                .region(Region.US_EAST_2)
                .build();

        List<Message> messages = new ArrayList<Message>();
        messages.add(Message.builder()
                .role(ConversationRole.fromValue("user"))
                .content(ContentBlock.fromText("What's the cosine of 1.57 radians?"))
                .build());

        ToolConfiguration.Builder toolConfig = ToolConfiguration.builder();

        List<Tool> tools = new ArrayList<>();
        // Add the cosine tool to the tool configuration
        String cosineContents = readDefinitionFile("src/main/java/bitovi/common/tools/ConsineTool/definition.json");
        if (cosineContents == null) {
            throw new LLMProviderException("Failed to read cosine tool definition file.");
        }
        tools.add(getCosineToolSpec());

        // Add the search tool to the tool configuration
        String searchContents = readDefinitionFile("src/main/java/bitovi/common/tools/SearchTool/definition.json");
        if (searchContents == null) {
            throw new LLMProviderException("Failed to read search tool definition file.");
        }
        tools.add(getSearchToolSpec());

        toolConfig.tools(tools);

        ToolConfiguration tc = toolConfig.build();
        ConverseRequest request = ConverseRequest.builder()
                .modelId(AWS_MODEL_ARN)
                .messages(messages)
                .toolConfig(tc)
                .build();

        ConverseResponse response = converseWithToolsRecursive(messages, request, 0);
        if (response == null || response.output() == null || response.output().message() == null) {
            throw new LLMProviderException("No response received from Bedrock.");
        }

    }

    private static ConverseResponse converseWithToolsRecursive(List<Message> messages, ConverseRequest request,
            Integer depth)
            throws LLMProviderException {
        if (depth > 4) {
            throw new LLMProviderException("Exceeded maximum recursion depth for tool invocation.");
        }

        System.out.println("ConverseRequest: " + request.toString());

        ConverseResponse response = bedrockRuntimeClient.converse(request);

        for (ContentBlock block : response.output().message().content()) {
            if (block.toolUse() != null) {
                String result;

                ToolUseBlock toolUseBlock = block.toolUse();
                switch (toolUseBlock.name()) {
                    // One simple hardcoded tool for testing
                    case "calculate_cosine": {
                        double number = toolUseBlock.input().asMap().get("number").asNumber().doubleValue();
                        result = String.valueOf(CosineToolImpl.executeTool(number));
                        break;
                    }

                    case "web_search": {
                        String query = toolUseBlock.input().asMap().get("query").asString();
                        result = SearchToolmpl.executeTool(query);
                        break;
                    }

                    default: {
                        throw new LLMProviderException("Unknown tool used: " + toolUseBlock.name());

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

        // If no tool use was found, return the response
        return response;
    }

    public static String readDefinitionFile(String filePath) {
        // Read in the JSON file to get the tool definition
        String contents;
        try {
            contents = Config.readFile(filePath);
        } catch (Exception e) {
            System.err.println("Error reading tool definition file: " + e.getMessage());
            return null;
        }
        return contents;
    }

    public static Tool getCosineToolSpec() {
        Map<String, Document> latitudeMap = new HashMap<>();
        latitudeMap.put("type", Document.fromString("string"));
        latitudeMap.put("description", Document.fromString("Geographical WGS84 latitude of the location."));

        // Create the nested "longitude" object
        Map<String, Document> longitudeMap = new HashMap<>();
        longitudeMap.put("type", Document.fromString("string"));
        longitudeMap.put("description", Document.fromString("Geographical WGS84 longitude of the location."));

        // Create the "properties" object
        Map<String, Document> propertiesMap = new HashMap<>();
        propertiesMap.put("latitude", Document.fromMap(latitudeMap));
        propertiesMap.put("longitude", Document.fromMap(longitudeMap));

        // Create the "required" array
        List<Document> requiredList = new ArrayList<>();
        requiredList.add(Document.fromString("latitude"));
        requiredList.add(Document.fromString("longitude"));

        // Create the root object
        Map<String, Document> rootMap = new HashMap<>();
        rootMap.put("type", Document.fromString("object"));
        rootMap.put("properties", Document.fromMap(propertiesMap));
        rootMap.put("required", Document.fromList(requiredList));

        // Now create the Document representing the JSON schema
        Document document = Document.fromMap(rootMap);

        ToolSpecification specification = ToolSpecification.builder()
                .name("Weather_Tool")
                .description("Get the current weather for a given location, based on its WGS84 coordinates.")
                .inputSchema(ToolInputSchema.builder()
                        .json(document)
                        .build())
                .build();

        return Tool.builder()
                .toolSpec(specification)
                .build();
    }

    public static Tool getSearchToolSpec() {
        Map<String, Document> latitudeMap = new HashMap<>();
        latitudeMap.put("type", Document.fromString("string"));
        latitudeMap.put("description", Document.fromString("Geographical WGS84 latitude of the location."));

        // Create the nested "longitude" object
        Map<String, Document> longitudeMap = new HashMap<>();
        longitudeMap.put("type", Document.fromString("string"));
        longitudeMap.put("description", Document.fromString("Geographical WGS84 longitude of the location."));

        // Create the "properties" object
        Map<String, Document> propertiesMap = new HashMap<>();
        propertiesMap.put("latitude", Document.fromMap(latitudeMap));
        propertiesMap.put("longitude", Document.fromMap(longitudeMap));

        // Create the "required" array
        List<Document> requiredList = new ArrayList<>();
        requiredList.add(Document.fromString("latitude"));
        requiredList.add(Document.fromString("longitude"));

        // Create the root object
        Map<String, Document> rootMap = new HashMap<>();
        rootMap.put("type", Document.fromString("object"));
        rootMap.put("properties", Document.fromMap(propertiesMap));
        rootMap.put("required", Document.fromList(requiredList));

        // Now create the Document representing the JSON schema
        Document document = Document.fromMap(rootMap);

        ToolSpecification specification = ToolSpecification.builder()
                .name("Weather_Tool")
                .description("Get the current weather for a given location, based on its WGS84 coordinates.")
                .inputSchema(ToolInputSchema.builder()
                        .json(document)
                        .build())
                .build();

        return Tool.builder()
                .toolSpec(specification)
                .build();
    }
}
