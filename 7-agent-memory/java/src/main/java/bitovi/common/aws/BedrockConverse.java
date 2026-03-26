package bitovi.common.aws;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import bitovi.common.Config;
import bitovi.workflow.types.UsageMetadata;
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

    /**
     * Call Bedrock Converse API with system prompt and optional tools, returning
     * text response with usage metadata.
     * 
     * @param systemPrompt   The system prompt to guide the AI
     * @param messageHistory List of message history (empty for single-turn
     *                       conversations)
     * @param tools          List of tools available to the AI (null for no tools)
     * @param modelId        The Bedrock model ID to use
     * @return ModelResponseWithUsage containing the text response and usage
     *         metadata
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
            return new ModelResponseWithUsage(null, extractUsageMetadata(response));
        }

        // Extract text content
        StringBuilder textResponse = new StringBuilder();
        for (ContentBlock block : contentBlocks) {
            if (block.text() != null) {
                textResponse.append(block.text());
            }
        }

        if (textResponse.length() > 0) {
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
                    usage.totalTokens());
        }
        return new UsageMetadata(0, 0, 0);
    }
}
