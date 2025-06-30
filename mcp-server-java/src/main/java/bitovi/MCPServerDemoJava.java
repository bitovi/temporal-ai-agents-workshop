package bitovi;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebFluxSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.Prompt;
import io.modelcontextprotocol.spec.McpSchema.PromptArgument;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Hello world!
 *
 */
public class MCPServerDemoJava {
  public static void main(String[] args) {
    WebFluxSseServerTransportProvider transportProvider = new WebFluxSseServerTransportProvider(new ObjectMapper(),
        "/mcp/message");
    // Create a server with custom configuration
    McpSyncServer syncServer = McpServer.sync(transportProvider)
        .serverInfo("llm-workflows-temporal-mcp-demo", "1.0.0")
        .capabilities(ServerCapabilities.builder()
            .resources(false, true) // Enable resource support
            .tools(true) // Enable tool support
            .prompts(true) // Enable prompt support
            .logging() // Enable logging support
            .completions() // Enable completions support
            .build())
        .build();

    // Sync tool specification
    var schema = """
        {
          "type" : "object",
          "id" : "urn:jsonschema:Operation",
          "properties" : {
            "operation" : {
              "type" : "string"
            },
            "a" : {
              "type" : "number"
            },
            "b" : {
              "type" : "number"
            }
          }
        }
        """;
    var syncToolSpecification = new McpServerFeatures.SyncToolSpecification(
        new Tool("calculator", "Basic calculator", schema),
        (exchange, arguments) -> {
          // Tool implementation
          return new CallToolResult("result", false);
        });

    // Register tools, resources, and prompts
    syncServer.addTool(syncToolSpecification);

    // Sync resource specification
    var syncResourceSpecification = new McpServerFeatures.SyncResourceSpecification(
        new Resource("custom://resource", "name", "description", "mime-type", null),
        (exchange, request) -> {
          // Resource read implementation
          return new ReadResourceResult(null);
        });

    syncServer.addResource(syncResourceSpecification);

    // Sync prompt specification
    var syncPromptSpecification = new McpServerFeatures.SyncPromptSpecification(
        new Prompt("greeting", "description", List.of(
            new PromptArgument("name", "description", true))),
        (exchange, request) -> {
          // Prompt implementation
          List<PromptMessage> messages = List
              .of(new PromptMessage(Role.ASSISTANT, new TextContent("Hello, " + request.toString() + "!")));
          return new GetPromptResult("This is a prompt that greets the user", messages);
        });
    syncServer.addPrompt(syncPromptSpecification);

    // How do I actually start the server?
  }
}
