package bitovi;

import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.time.Duration;
import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;

public class BitoviMCPClient {
        public static void main(String[] args) {
                System.out.println("Bitovi MCP Client is running. This is a placeholder for future implementation.");
                // Future implementation will include connecting to the Bitovi MCP and handling
                // workflows.

                String LIFEFORCE_MCP_TOKEN = Config.getProperty("LIFEFORCE_MCP_TOKEN");

                // Create a transport for the repkam09 MCP API with an `authorization` header
                Builder builder = HttpRequest.newBuilder()
                                .header("Authorization", "Bearer " + LIFEFORCE_MCP_TOKEN)
                                .header("Accept", "text/event-stream")
                                .header("Cache-Control", "no-cache")
                                .header("Content-Type", "application/json");

                System.out.println("Using MCP Token: " + LIFEFORCE_MCP_TOKEN);

                // Create McpClientTransport using HttpClientSseClientTransport
                HttpClientSseClientTransport transport = HttpClientSseClientTransport
                                .builder("https://api.repkam09.com/api/mcp")
                                .sseEndpoint("https://api.repkam09.com/api/mcp")
                                .requestBuilder(builder)
                                .build();

                // Create a sync client with custom configuration
                McpSyncClient client = McpClient.sync(transport)
                                .requestTimeout(Duration.ofSeconds(10))
                                .build();

                // Initialize connection
                client.initialize();

                // List available tools
                ListToolsResult tools = client.listTools();
                System.out.println("Available tools: " + tools.tools().size());

                // Find the tool called 'weather-current' and call it with a
                // zip code as an example
                tools.tools().stream()
                                .filter(tool -> tool.name().equals("weather-current"))
                                .findFirst()
                                .ifPresentOrElse(
                                                tool -> {
                                                        // Call the tool with a sample zip code
                                                        CallToolResult result = client.callTool(
                                                                        new CallToolRequest(tool.name(),
                                                                                        Map.of("zipCode", "14543")));
                                                        System.out.println("Tool result: " + result.content());
                                                },
                                                () -> System.out.println("Tool 'weather-current' not found"));

                // Close client
                client.closeGracefully();
                System.exit(0);
        }
}
