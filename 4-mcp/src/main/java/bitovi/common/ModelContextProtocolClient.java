package bitovi.common;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities;
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
        String MCP_SERVER_BASE_URL = config.getProperty("MCP_SERVER_BASE_URL");
        String MCP_SERVER_SSE_URL = config.getProperty("MCP_SERVER_SSE_URL");

        // Create McpClientTransport using HttpClientSseClientTransport
        HttpClientSseClientTransport transport = HttpClientSseClientTransport
                .builder(MCP_SERVER_BASE_URL)
                .sseEndpoint(MCP_SERVER_SSE_URL)
                .build();

        // Create a sync client with custom configuration
        this.mcpClient = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(30))
                .capabilities(ClientCapabilities.builder()
                        .roots(true) // Enable roots capability
                        .build())
                .build();

        // Initialize connection
        this.mcpClient.initialize();
    }

    public List<software.amazon.awssdk.services.bedrockruntime.model.Tool> getAvailableTools()
            throws JsonProcessingException {
        if (availableTools == null || availableTools.isEmpty()) {
            this.availableTools = new ArrayList<software.amazon.awssdk.services.bedrockruntime.model.Tool>();
            ListToolsResult tools = mcpClient.listTools();

            // Convert MCP tools to Bedrock runtime tool format
            for (Tool mcpTool : tools.tools()) {
                JsonSchema inputSchema = mcpTool.inputSchema();
                if (inputSchema == null) {
                    continue;
                }

                software.amazon.awssdk.services.bedrockruntime.model.Tool bedrockRuntimeTool = software.amazon.awssdk.services.bedrockruntime.model.Tool
                        .builder()
                        .toolSpec(ToolSpecification.builder()
                                .name(mcpTool.name())
                                .description(mcpTool.description())
                                .inputSchema(ToolInputSchema.builder()
                                        .json(inputSchemaToDocument(inputSchema))
                                        .build())
                                .build())
                        .build();

                availableTools.add(bedrockRuntimeTool);
            }
        }
        return availableTools;
    }

    public String executeMCPTool(String toolName, Map<String, Object> arguments) {
        if (!this.mcpClient.isInitialized()) {
            throw new IllegalStateException("MCP Client is not initialized. Please call initialize() first.");
        }

        try {
            CallToolRequest req = new CallToolRequest(toolName, arguments);
            CallToolResult result = mcpClient.callTool(req);

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

    private Document inputSchemaToDocument(JsonSchema inputSchema) throws JsonProcessingException {
        // Loop over the properties and convert them to Documents
        Map<String, Document> propertiesMap = new HashMap<>();
        if (inputSchema.properties() != null) {
            for (Map.Entry<String, Object> entry : inputSchema.properties().entrySet()) {
                String key = entry.getKey();
                Map<String, Object> value = (Map<String, Object>) entry.getValue();

                Map<String, Document> propertyMap = new HashMap<>();
                if (value.get("type") != null) {
                    propertyMap.put("type", Document.fromString(value.get("type").toString()));
                }

                if (value.get("description") != null) {
                    propertyMap.put("description", Document.fromString(value.get("description").toString()));
                }

                Document valueDoc = Document.fromMap(propertyMap);
                propertiesMap.put(key, valueDoc);
            }
        }

        // Create the required list
        List<Document> requiredList = new ArrayList<>();
        if (inputSchema.required() != null) {
            for (String requiredField : inputSchema.required()) {
                requiredList.add(Document.fromString(requiredField));
            }
        }

        Map<String, Document> rootMap = new HashMap<>();
        rootMap.put("type", Document.fromString("object"));
        rootMap.put("properties", Document.fromMap(propertiesMap));
        rootMap.put("required", Document.fromList(requiredList));
        rootMap.put("additionalProperties", Document.fromBoolean(false));

        // Now create the Document representing the JSON schema
        Document document = Document.fromMap(rootMap);

        return document;
    }
}
