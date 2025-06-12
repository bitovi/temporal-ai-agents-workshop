package bitovi;

import java.net.http.HttpRequest;
import java.util.List;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

public class App {
        public static void main(String[] args) {
                System.out.println("Hello World!");

                ChatModel ollama = OllamaChatModel.builder()
                                .baseUrl(Config.getProperty("OLLAMA_HOST", "http://localhost:11434"))
                                .temperature(0.7)
                                .logRequests(true)
                                .logResponses(true)
                                .modelName(Config.getProperty("OLLAMA_MODEL_ID", "mistral:latest"))
                                .build();

                String userMessage = "Write a 100-word poem about Java and AI";
                chat(ollama, userMessage);

                String AWS_ACCESS_KEY_ID = Config.getProperty("AWS_ACCESS_KEY_ID");
                String AWS_SECRET_ACCESS_KEY = Config.getProperty("AWS_SECRET_ACCESS_KEY");
                String AWS_SESSION_TOKEN = Config.getProperty("AWS_SESSION_TOKEN");

                var bedrockRuntimeClient = BedrockRuntimeClient.builder()
                                .credentialsProvider(StaticCredentialsProvider.create(
                                                AwsSessionCredentials.create(
                                                                AWS_ACCESS_KEY_ID,
                                                                AWS_SECRET_ACCESS_KEY,
                                                                AWS_SESSION_TOKEN)))
                                .region(Region.US_EAST_2)
                                .build();

                BedrockChatModel bedrock = BedrockChatModel.builder().modelId(Config.getProperty("AWS_MODEL_ARN"))
                                .client(bedrockRuntimeClient)
                                .build();
                chat(bedrock, userMessage);
        }

        private static void chat(ChatModel model, String userMessage) {

                String LIFEFORCE_MCP_TOKEN = Config.getProperty("LIFEFORCE_MCP_TOKEN");

                // Create a transport for the MCP API with authorization header
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                                .header("Authorization", "Bearer " + LIFEFORCE_MCP_TOKEN)
                                .header("Accept", "text/event-stream")
                                .header("Cache-Control", "no-cache")
                                .header("Content-Type", "application/json");

                // TODO: https://github.com/langchain4j/langchain4j/pull/2899
                McpTransport transport = new HttpMcpTransport.Builder()
                                .sseUrl("https://api.repkam09.com/api/mcp")
                                .logRequests(true) // if you want to see the traffic in the log
                                .logResponses(true)
                                .build();

                McpClient mcpClient = new DefaultMcpClient.Builder()
                                .transport(transport)
                                .build();

                ToolProvider toolProvider = McpToolProvider.builder()
                                .mcpClients(List.of(mcpClient))
                                .build();

                Bot bot = AiServices.builder(Bot.class)
                                .chatModel(model)
                                .toolProvider(toolProvider)
                                .build();

                String result = bot.chat(userMessage);
                System.out.println("Response " + bot.toString() + ": " + result);
        }
}
