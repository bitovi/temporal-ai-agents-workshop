package bitovi;

import java.util.ArrayList;
import java.util.List;

import bitovi.common.tools.CosineToolImpl;
import bitovi.common.tools.SearchToolmpl;
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
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock;
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
                .content(ContentBlock.fromText("Could you show me the search results for Java Programming?"))
                .build());

        ToolConfiguration.Builder toolConfig = ToolConfiguration.builder();

        List<Tool> tools = new ArrayList<>();
        tools.add(CosineToolImpl.getBedrockTool());
        tools.add(SearchToolmpl.getBedrockTool());

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

        // Print the final response message
        System.out.println("Final Response: " + response.output().message().content().get(0).text());
    }

    private static ConverseResponse converseWithToolsRecursive(List<Message> messages, ConverseRequest request,
            Integer depth)
            throws LLMProviderException {
        if (depth > 4) {
            throw new LLMProviderException("Exceeded maximum recursion depth for tool invocation.");
        }

        ConverseResponse response = bedrockRuntimeClient.converse(request);

        for (ContentBlock block : response.output().message().content()) {
            if (block.toolUse() != null) {
                String result;

                ToolUseBlock toolUseBlock = block.toolUse();
                Document toolUseInput = toolUseBlock.input();
                switch (toolUseBlock.name()) {
                    // One simple hardcoded tool for testing
                    case "calculate_cosine": {
                        result = CosineToolImpl.execute(toolUseInput);
                        break;
                    }

                    case "web_search": {
                        result = SearchToolmpl.execute(toolUseInput);
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
        if (response.output().message() == null) {
            throw new LLMProviderException("No message content in response.");
        }
        return response;
    }
}
