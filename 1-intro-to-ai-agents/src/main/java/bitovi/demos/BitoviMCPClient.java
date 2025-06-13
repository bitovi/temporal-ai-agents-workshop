package bitovi.demos;

import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.time.Duration;

import bitovi.Config;
import bitovi.providers.MCPToolIntegration;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
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

                // Loop over all the tools and convert them to Bedrock format
                tools.tools().stream().forEach(tool -> {
                        String converted = MCPToolIntegration.convertMCPSchemaToBedrockSchema(tool);
                        System.out.println(converted);
                });

                // Close client
                client.closeGracefully();
                System.exit(0);
        }
}
