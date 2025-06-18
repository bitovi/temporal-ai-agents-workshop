package bitovi.demos;

import java.util.ArrayList;
import java.util.List;
import bitovi.common.Config;
import bitovi.common.LLMProviderException;
import bitovi.common.tools.SummarizeEmailImpl;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.AnyToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

public class BitoviBedrockStructureOutput {

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

                StringBuilder sb = new StringBuilder();
                sb.append("<content>");
                sb.append("Dear Acme Investments,");
                sb.append(
                                "I am writing to compliment one of your customer service representatives, Shirley Scarry. I recently had the pleasure of speaking with Shirley regarding my account deposit. Shirley was extremely helpful and knowledgeable, and went above and beyond to ensure that all of my questions were answered. Shirley also had Robert Herbford join the call, who wasn't quite as helpful. My wife, Clara Bradford, didn't like him at all.");
                sb.append(
                                "Shirley's professionalism and expertise were greatly appreciated, and I would be happy to recommend Acme Investments to others based on my experience.");
                sb.append("Sincerely,");
                sb.append("Carson Bradford");
                sb.append("</content>");
                sb.append(
                                "Please use the summarize_email tool to generate the email summary JSON based on the content within the <content> tags.");

                messages.add(Message.builder()
                                .role(ConversationRole.fromValue("user"))
                                .content(ContentBlock.fromText(
                                                sb.toString()))
                                .build());

                ToolConfiguration.Builder toolConfig = ToolConfiguration.builder();

                List<Tool> tools = new ArrayList<>();
                tools.add(SummarizeEmailImpl.getBedrockTool());

                toolConfig.tools(tools);
                toolConfig.toolChoice(
                                ToolChoice.builder().any(AnyToolChoice.builder().build()).build());

                ToolConfiguration tc = toolConfig.build();
                ConverseRequest request = ConverseRequest.builder()
                                .modelId(AWS_MODEL_ARN)
                                .messages(messages)
                                .toolConfig(tc)
                                .build();

                String response = converseWithToolsRecursive(messages, request, 0);
                if (response == null) {
                        throw new LLMProviderException("No response from model.");
                }

                // Print the final response message
                System.out.println("Final Response: " + response);
        }

        private static String converseWithToolsRecursive(List<Message> messages, ConverseRequest request,
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
                                        case "summarize_email": {
                                                result = SummarizeEmailImpl.execute(toolUseInput);
                                                return result;
                                        }

                                        default: {
                                                throw new LLMProviderException("Expected summarize_email only.");
                                        }
                                }

                                // Add the tool result to the messages

                        }

                        messages.add(response.output().message());
                        return response.output().message().content().toString(); // No tool use, return the response
                }

                // If no tool use was found, return the response
                if (response.output().message() == null) {
                        throw new LLMProviderException("No message content in response.");
                }
                return null;
        }
}
