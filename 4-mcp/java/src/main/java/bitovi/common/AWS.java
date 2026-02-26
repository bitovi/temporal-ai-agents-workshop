package bitovi.common;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.temporal.failure.ApplicationFailure;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

public class AWS {

    public record ModelToolCall(String toolName, Map<String, Object> toolInputs) {

    }

    public record ModelResponse(String response, ModelToolCall toolCall) {
    }

    public record ChatMessage(String role, String content) {
    }

    private static Config config = new Config();

    public static AwsCredentialsProvider getAwsCredentialsProvider() {
        String AWS_ACCESS_KEY_ID = config.getProperty("AWS_ACCESS_KEY_ID");
        String AWS_SECRET_ACCESS_KEY = config.getProperty("AWS_SECRET_ACCESS_KEY");
        String AWS_SESSION_TOKEN = config.getProperty("AWS_SESSION_TOKEN");

        StaticCredentialsProvider credentialsProvider;

        if (AWS_SESSION_TOKEN == null || AWS_SESSION_TOKEN.isEmpty()) {
            credentialsProvider = StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                            AWS_ACCESS_KEY_ID,
                            AWS_SECRET_ACCESS_KEY));
        } else {
            credentialsProvider = StaticCredentialsProvider.create(
                    AwsSessionCredentials.create(
                            AWS_ACCESS_KEY_ID,
                            AWS_SECRET_ACCESS_KEY,
                            AWS_SESSION_TOKEN));
        }
        return credentialsProvider;
    }

    public static Region getAwsRegion() {
        return Region.of(config.getProperty("AWS_REGION"));
    }

    public static BedrockRuntimeClient getBedrockRuntimeClient() {
        return BedrockRuntimeClient.builder()
                .credentialsProvider(AWS.getAwsCredentialsProvider())
                .region(AWS.getAwsRegion())
                .build();

    }

    public static List<Float> calculateEmbedding(String input) {
        String AWS_EMBEDDING_MODEL_ID = config.getProperty("AWS_EMBEDDING_MODEL_ID");

        JSONObject jsonBody = new JSONObject()
                .put("inputText", input);

        SdkBytes body = SdkBytes.fromUtf8String(jsonBody.toString());
        InvokeModelRequest request = InvokeModelRequest.builder()
                .modelId(AWS_EMBEDDING_MODEL_ID)
                .contentType("application/json")
                .accept("*/*")
                .body(body)
                .build();

        BedrockRuntimeClient bedrockRuntimeClient = AWS.getBedrockRuntimeClient();
        InvokeModelResponse response = bedrockRuntimeClient.invokeModel(request);

        JSONObject responseJson = new JSONObject(
                response.body().asString(StandardCharsets.UTF_8));

        List<Float> embedding = responseJson.getJSONArray("embedding").toList().stream()
                .map(obj -> ((Number) obj).floatValue())
                .toList();

        if (embedding.isEmpty()) {
            return List.of();
        }

        return embedding;
    }

    public static ModelResponse bedrockConverse(List<ChatMessage> history) {
        Config config = new Config();
        String AWS_MODEL_ARN = config.getProperty("AWS_MODEL_ID");
        List<Message> messages = new ArrayList<Message>();

        // Convert the chat messages to Bedrock's Message format
        for (ChatMessage message : history) {
            messages.add(Message.builder()
                    .role(ConversationRole.fromValue(message.role()))
                    .content(ContentBlock.fromText(message.content()))
                    .build());
        }

        ToolConfiguration.Builder toolConfig = ToolConfiguration.builder();

        // TODO_MCP: Take a look at this method and compare it to what we did in the
        // previous Tool Calling exercise.
        ModelContextProtocolClient mcpIntegration = new ModelContextProtocolClient();
        try {
            List<Tool> availableTools = mcpIntegration.getAvailableTools();
            toolConfig.tools(availableTools);
        } catch (JsonProcessingException err) {
            throw ApplicationFailure.newNonRetryableFailureWithCause("Error parsing tools from MCP server",
                    "JsonProcessingException", err, err.getMessage());
        }

        String systemPrompt = "You are a helpful assistant that can answer questions and call tools when needed. If you need to call a tool to fetch more information, be sure to do so.";

        ConverseRequest request = ConverseRequest.builder()
                .modelId(AWS_MODEL_ARN)
                .messages(messages)
                .toolConfig(toolConfig.build())
                .system(SystemContentBlock.fromText(systemPrompt))
                .build();

        BedrockRuntimeClient bedrockRuntimeClient = AWS.getBedrockRuntimeClient();
        ConverseResponse response = bedrockRuntimeClient.converse(request);

        List<ContentBlock> contentBlocks = response.output().message().content();

        System.out.println("Model response contained " + contentBlocks.size() + " content blocks.");

        // Grab any content block that has a tool call first
        if (contentBlocks.isEmpty()) {
            System.out.println("Model did not respond with any content blocks.");
            return new ModelResponse(null, null);
        }

        // If the response contains a tool call, we will handle it first
        StringBuilder textResponse = new StringBuilder();
        for (ContentBlock block : contentBlocks) {
            if (block.text() != null) {
                textResponse.append(block.text());
            }

            if (block.toolUse() != null) {
                ToolUseBlock toolUseBlock = block.toolUse();

                // If the response is a tool call, return the tool name and inputs
                String toolName = toolUseBlock.name();
                Document toolInputs = toolUseBlock.input();

                Map<String, Object> toolInputsMap = toDocumentMap(toolUseBlock);
                try {
                    System.out.println(
                            "Model requested tool call: " + toolName + " with inputs: " + toolInputs.toString());

                    return new ModelResponse(null, new ModelToolCall(toolName, toolInputsMap));
                } catch (Exception e) {
                    throw ApplicationFailure.newNonRetryableFailureWithCause("Error parsing tool inputs",
                            "InvalidToolInputs", e, toolInputs.toString());
                }
            }
        }

        if (textResponse.length() > 0) {
            System.out.println("Model response text: " + textResponse.toString());
            return new ModelResponse(textResponse.toString(), null);
        }

        // If we reach here, we didn't get a valid response
        System.out.println("Model did not respond with text or tool call.");
        throw ApplicationFailure.newNonRetryableFailure(response.toString(),
                "UnexpectedModelResponseShape");
    }

    private static Map<String, Object> toDocumentMap(ToolUseBlock toolUseBlock) {
        Map<String, Object> objectMap = new java.util.HashMap<>();
        Map<String, Document> docMap = toolUseBlock
                .input().asMap();
        for (Map.Entry<String, Document> entry : docMap
                .entrySet()) {
            objectMap.put(entry.getKey(), entry.getValue().toString());
        }
        return objectMap;
    }
}
