package bitovi.common.aws;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import bitovi.common.Config;
import bitovi.workflow.types.UsageMetadata;
import io.temporal.failure.ApplicationFailure;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest.Builder;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

public class BedrockConverse {

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

    public static ModelResponse bedrockConverse(List<ChatMessage> history, String prompt,
            ToolConfiguration toolConfig) {
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

        Builder request = ConverseRequest.builder()
                .modelId(AWS_MODEL_ARN)
                .messages(messages)
                .system(SystemContentBlock.fromText(prompt.trim()));

        if (toolConfig != null) {
            request.toolConfig(toolConfig);
        }

        BedrockRuntimeClient bedrockRuntimeClient = AWS.getBedrockRuntimeClient();

        List<ContentBlock> contentBlocks = null;
        ConverseResponse response = bedrockRuntimeClient.converse(request
                .build());

        contentBlocks = response.output().message().content();

        // Grab any content block that has a tool call first
        if (contentBlocks == null || contentBlocks.isEmpty()) {
            System.out.println("Model did not respond with any content blocks.");
            return new ModelResponse(null, null);
        }

        System.out.println("Model response contained " + contentBlocks.size() + " content blocks.");

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

        return new ModelResponse(null, null);
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

        BedrockRuntimeClient bedrockRuntimeClient = AWS.getBedrockRuntimeClient();
        ConverseResponse response = bedrockRuntimeClient.converse(requestBuilder.build());

        List<ContentBlock> contentBlocks = response.output().message().content();

        if (contentBlocks == null || contentBlocks.isEmpty()) {
            System.out.println("Model did not respond with any content blocks.");
            return new ModelResponseWithUsage(null, extractUsageMetadata(response));
        }

        System.out.println("Model response contained " + contentBlocks.size() + " content blocks.");

        // Extract text content
        StringBuilder textResponse = new StringBuilder();
        for (ContentBlock block : contentBlocks) {
            if (block.text() != null) {
                textResponse.append(block.text());
            }
        }

        if (textResponse.length() > 0) {
            System.out.println("Model response text: " + textResponse.toString());
            return new ModelResponseWithUsage(textResponse.toString(), extractUsageMetadata(response));
        }

        return new ModelResponseWithUsage(null, extractUsageMetadata(response));
    }

    /**
     * Extract usage metadata from a ConverseResponse.
     * 
     * @param response The ConverseResponse from Bedrock
     * @return UsageMetadata with token counts
     */
    private static UsageMetadata extractUsageMetadata(ConverseResponse response) {
        TokenUsage usage = response.usage();
        if (usage != null) {
            return new UsageMetadata(
                usage.inputTokens(),
                usage.outputTokens(),
                usage.totalTokens()
            );
        }
        return new UsageMetadata(0, 0, 0);
    }
}
