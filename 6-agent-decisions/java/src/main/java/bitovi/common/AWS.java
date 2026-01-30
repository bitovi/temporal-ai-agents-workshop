package bitovi.common;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import bitovi.workflow.types.UsageMetadata;
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
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest.Builder;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

public class AWS {

    public record ModelToolCall(String toolName, Map<String, Object> toolInputs) {

    }

    public record ModelResponse(String response, ModelToolCall toolCall) {
    }
    
    public record ModelResponseWithUsage(String response, UsageMetadata usage) {
    }

    public record ChatMessage(String role, String content) {
    }

    public record ToolResult(String output, boolean isFinalResult) {

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

    /**
     * Call Bedrock Converse API with system prompt and optional tools, returning text response with usage metadata.
     * 
     * @param systemPrompt The system prompt to guide the AI
     * @param messageHistory List of message history (empty for single-turn conversations)
     * @param tools List of tools available to the AI (null for no tools)
     * @param modelId The Bedrock model ID to use
     * @return ModelResponseWithUsage containing the text response and usage metadata
     */
    public static ModelResponseWithUsage bedrockConverseWithUsage(
            String systemPrompt, 
            List<ChatMessage> messageHistory, 
            List<Tool> tools,
            String modelId) {
        
        List<Message> messages = new ArrayList<>();

        // Convert the chat messages to Bedrock's Message format
        if (messageHistory != null) {
            for (ChatMessage message : messageHistory) {
                messages.add(Message.builder()
                        .role(ConversationRole.fromValue(message.role()))
                        .content(ContentBlock.fromText(message.content()))
                        .build());
            }
        }

        Builder requestBuilder = ConverseRequest.builder()
                .modelId(modelId)
                .messages(messages);

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            requestBuilder.system(SystemContentBlock.fromText(systemPrompt.trim()));
        }

        if (tools != null && !tools.isEmpty()) {
            ToolConfiguration toolConfig = ToolConfiguration.builder()
                    .tools(tools)
                    .build();
            requestBuilder.toolConfig(toolConfig);
        }

        // Configure reasoning parameters with a 2000 token budget
        Document reasoningConfig = Document.mapBuilder()
                .putDocument("reasoningConfig", Document.mapBuilder()
                        .putString("type", "enabled")
                        .putString("maxReasoningEffort", "low")
                        .build())
                .build();
        requestBuilder.additionalModelRequestFields(reasoningConfig);

        BedrockRuntimeClient bedrockRuntimeClient = AWS.getBedrockRuntimeClient();
        ConverseResponse response = bedrockRuntimeClient.converse(requestBuilder.build());

        List<ContentBlock> contentBlocks = response.output().message().content();

        if (contentBlocks == null || contentBlocks.isEmpty()) {
            System.out.println("Model did not respond with any content blocks.");
            return new ModelResponseWithUsage(null, extractUsageMetadata(response, null));
        }

        System.out.println("Model response contained " + contentBlocks.size() + " content blocks.");

        // Extract text content
        StringBuilder textResponse = new StringBuilder();
        for (ContentBlock block : contentBlocks) {
            if (block.reasoningContent() != null) {
                System.out.println("Model reasoning output: " + block.reasoningContent().reasoningText().text());
            }
            else if (block.text() != null) {
                textResponse.append(block.text());
            }
        }

        if (textResponse.length() > 0) {
            System.out.println("Model response text: " + textResponse.toString());
            return new ModelResponseWithUsage(textResponse.toString(), extractUsageMetadata(response, textResponse.toString()));
        }

        return new ModelResponseWithUsage(null, extractUsageMetadata(response, null));
    }

    /**
     * Extract usage metadata from a ConverseResponse.
     * 
     * @param response The ConverseResponse from Bedrock
     * @return UsageMetadata with token counts
     */
    private static UsageMetadata extractUsageMetadata(ConverseResponse response, String finalOutput) {
        int finalOutputTokens = ModelUtils.estimateTokenCount(finalOutput);
        TokenUsage usage = response.usage();
        if (usage != null) {
            return new UsageMetadata(
                usage.inputTokens(),
                usage.outputTokens(),
                usage.outputTokens() - finalOutputTokens,
                usage.totalTokens()
            );
        }
        return new UsageMetadata(0, 0, 0,0);
    }
}
