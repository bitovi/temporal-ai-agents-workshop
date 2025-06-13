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

/**
 * Integration layer that bridges MCP server tools with AWS Bedrock Runtime
 * models
 */
public class ModelContextProtocolClient {

    private McpSyncClient mcpClient;
    private List<Tool> availableTools;

    public ModelContextProtocolClient() {
        String LIFEFORCE_MCP_TOKEN = Config.getProperty("LIFEFORCE_MCP_TOKEN");

        // Create a transport for the MCP API with authorization header
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .header("Authorization", "Bearer " + LIFEFORCE_MCP_TOKEN)
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache")
                .header("Content-Type", "application/json");

        // Create McpClientTransport using HttpClientSseClientTransport
        HttpClientSseClientTransport transport = HttpClientSseClientTransport
                .builder("https://api.repkam09.com/api/mcp")
                .sseEndpoint("https://api.repkam09.com/api/mcp")
                .requestBuilder(builder)
                .build();

        // Create a sync client with custom configuration
        this.mcpClient = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(10))
                .build();

        // Initialize connection
        this.mcpClient.initialize();
    }

    /**
     * Convert MCP tools to Bedrock tool specifications
     */
    public List<software.amazon.awssdk.services.bedrockruntime.model.Tool> getBedrockToolSpecifications() {
        List<software.amazon.awssdk.services.bedrockruntime.model.Tool> bedrockTools = new ArrayList<>();

        // Ensure we have the latest tools loaded
        getAvailableTools();

        for (Tool mcpTool : availableTools) {
            try {
                String converted = convertMCPSchemaToBedrockSchema(mcpTool);

                software.amazon.awssdk.services.bedrockruntime.model.Tool bedrockTool = software.amazon.awssdk.services.bedrockruntime.model.Tool
                        .builder()
                        .toolSpec(ToolSpecification.builder()
                                .name(mcpTool.name())
                                .description(mcpTool.description())
                                .inputSchema(ToolInputSchema.builder()
                                        .json(Document.fromString(converted))
                                        .build())
                                .build())
                        .build();

                bedrockTools.add(bedrockTool);
            } catch (Exception e) {
                System.err.println("Failed to convert MCP tool " + mcpTool.name() + ": " + e.getMessage());
            }
        }

        return bedrockTools;
    }

    // Execute an MCP tool call
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

    // Convert MCP input schema to Bedrock-compatible JSON schema
    public static String convertMCPSchemaToBedrockSchema(Tool mcpTool) {
        System.out.println(
                "Converting MCP tool schema to Bedrock format: " + mcpTool.name() + " - " + mcpTool.description());

        ObjectMapper objectMapper = new ObjectMapper();

        JsonSchema inputSchema = mcpTool.inputSchema();

        ObjectNode bedrockTool = objectMapper.createObjectNode();
        bedrockTool.put("type", "object");
        bedrockTool.put("properties", objectMapper.valueToTree(inputSchema.properties()));
        bedrockTool.put("required", objectMapper.valueToTree(inputSchema.required()));
        bedrockTool.put("additionalProperties", inputSchema.additionalProperties() != null
                ? objectMapper.valueToTree(inputSchema.additionalProperties())
                : BooleanNode.FALSE);

        String bedrockJson;
        try {
            bedrockJson = objectMapper.writeValueAsString(bedrockTool);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return "{}"; // Return empty JSON object on error
        }

        System.out.println("Converted MCP tool schema to Bedrock format: " + bedrockJson);
        return bedrockJson;
    }

    public List<Tool> getAvailableTools() {
        if (availableTools == null || availableTools.isEmpty()) {
            try {
                ListToolsResult tools = mcpClient.listTools();
                this.availableTools = tools.tools();
                System.out.println("Loaded " + availableTools.size() + " MCP tools");
            } catch (Exception e) {
                System.err.println("Failed to load MCP tools: " + e.getMessage());
                this.availableTools = new ArrayList<Tool>();
            }
        }
        return availableTools;
    }

    public void close() {
        if (mcpClient != null) {
            mcpClient.closeGracefully();
        }
    }
}
