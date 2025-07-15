package bitovi.common;

import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;

public class ModelContextProtocolClient {

    private McpSyncClient mcpClient;
    private List<software.amazon.awssdk.services.bedrockruntime.model.Tool> availableTools;

    public ModelContextProtocolClient() {
        Config config = new Config();
        String MCP_SERVER_API_KEY = config.getProperty("MCP_SERVER_API_KEY");
        String MCP_SERVER_BASE_URL = config.getProperty("MCP_SERVER_BASE_URL");
        String MCP_SERVER_SSE_URL = config.getProperty("MCP_SERVER_SSE_URL");

        // Create a transport for the MCP API with authorization header
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .header("Authorization", "Bearer " + MCP_SERVER_API_KEY)
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache")
                .header("Content-Type", "application/json");

        // Create McpClientTransport using HttpClientSseClientTransport
        HttpClientSseClientTransport transport = HttpClientSseClientTransport
                .builder(MCP_SERVER_BASE_URL)
                .sseEndpoint(MCP_SERVER_SSE_URL)
                .requestBuilder(builder)
                .build();

        // Create a sync client with custom configuration
        this.mcpClient = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(10))
                .build();

        // Initialize connection
        this.mcpClient.initialize();
    }

    public List<software.amazon.awssdk.services.bedrockruntime.model.Tool> getAvailableTools()
            throws JsonProcessingException {
        if (availableTools == null || availableTools.isEmpty()) {
            this.availableTools = new ArrayList<software.amazon.awssdk.services.bedrockruntime.model.Tool>();
            ListToolsResult tools = mcpClient.listTools();
            System.out.println("Loaded " + availableTools.size() + " MCP tools");

            // Convert MCP tools to Bedrock runtime tool format
            for (Tool mcpTool : tools.tools()) {
                System.out.println("Tool: " + mcpTool.name() + ", Description: " + mcpTool.description());

                ObjectMapper objectMapper = new ObjectMapper();
                JsonSchema inputSchema = mcpTool.inputSchema();

                ObjectNode bedrockTool = objectMapper.createObjectNode();
                bedrockTool.put("type", "object");
                bedrockTool.set("properties", objectMapper.valueToTree(inputSchema.properties()));
                bedrockTool.set("required", objectMapper.valueToTree(inputSchema.required()));
                bedrockTool.set("additionalProperties", inputSchema.additionalProperties() != null
                        ? objectMapper.valueToTree(inputSchema.additionalProperties())
                        : BooleanNode.FALSE);

                String converted = objectMapper.writeValueAsString(bedrockTool);

                software.amazon.awssdk.services.bedrockruntime.model.Tool bedrockRuntimeTool = software.amazon.awssdk.services.bedrockruntime.model.Tool
                        .builder()
                        .toolSpec(ToolSpecification.builder()
                                .name(mcpTool.name())
                                .description(mcpTool.description())
                                .inputSchema(ToolInputSchema.builder()
                                        .json(Document.fromString(converted))
                                        .build())
                                .build())
                        .build();

                availableTools.add(bedrockRuntimeTool);
            }
        }
        return availableTools;
    }

    public String executeMCPTool(String toolName, Map<String, Object> arguments) {
        try {
            CallToolResult result = mcpClient.callTool(new CallToolRequest(toolName, arguments));

            // Extract content from the result - content() returns a list of Content objects
            if (result.content() != null && !result.content().isEmpty()) {
                // Get the first content item and convert to string
                return result.content().toString();
            }

            return "Tool executed successfully but returned no content";
        } catch (Exception e) {
            System.err.println("Failed to execute MCP tool " + toolName + ": " + e.getMessage());
            return "Error executing tool: " + e.getMessage();
        }
    }
}
