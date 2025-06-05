package bitovi;

import java.util.ArrayList;

import bitovi.providers.LLMProvider;
import bitovi.providers.LLMProviderChatMessage;
import bitovi.providers.OllamaProvider;
import bitovi.providers.BedrockProvider;

public class RepkaStandalone {
        public static void main(String[] args) throws Exception {
                System.out.println("Creating Ollama Instance...");
                OllamaProvider ollamaProvider = new OllamaProvider();
                test(ollamaProvider);

                System.out.println("Creating Bedrock Instance...");
                BedrockProvider bedrockProvider = new BedrockProvider();
                test(bedrockProvider);
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
