package bitovi;

import java.util.ArrayList;

import bitovi.providers.LLMProvider;
import bitovi.providers.LLMProviderChatMessage;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import bitovi.common.tools.ConsineTool.CosineToolImpl;
import bitovi.providers.BedrockProvider;

public class RepkaStandalone {
        public static void main(String[] args) throws Exception {
                System.out.println("Verifying Config...");
                String exists = Config.getProperty("CONFIG_EXISTS");
                if (exists == null || !exists.equals("true")) {
                        System.out.println("Config was not loaded or is missing.");
                        return;
                }

                System.out.println("Creating Bedrock Instance...");
                BedrockProvider bedrockProvider = new BedrockProvider();

                ArrayList<LLMProviderChatMessage> chatHistory = new ArrayList<LLMProviderChatMessage>();
                chatHistory.add(new LLMProviderChatMessage("user", "What is the cosine of 1.57 radians?"));

                ArrayList<Tool> tools = new ArrayList<Tool>();
                tools.add(CosineToolImpl.getBedrockToolSpecification());

                LLMProviderChatMessage chatResponse = bedrockProvider.chatWithTools(chatHistory, tools);
                System.out.println("Answer: " + chatResponse.getContent());

                // System.out.println("Creating Ollama Instance...");
                // OllamaProvider ollamaProvider = new OllamaProvider();
                // test(ollamaProvider);
        }

        public static void test(LLMProvider provider) throws Exception {
                System.out.println("Creating Completion Request...");
                String completionResponse = provider.completion("Tell me about yourself.");
                System.out.println("Completion Response: " + completionResponse);

                System.out.println("Sending Chat Request...");
                ArrayList<LLMProviderChatMessage> chatHistory = new ArrayList<LLMProviderChatMessage>();
                chatHistory.add(new LLMProviderChatMessage("user", "Tell me about yourself."));
                LLMProviderChatMessage chatResponse = provider.chat(chatHistory);

                System.out.println("Chat Result Received.");
                System.out.println("First answer: " + chatResponse.getContent());
        }
}
