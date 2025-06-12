package bitovi;

import java.util.ArrayList;

import bitovi.providers.BedrockProvider;
import bitovi.providers.MCPToolIntegration;
import bitovi.records.MessageRecord;

/**
 * Simple standalone example of AWS Bedrock + MCP Tools integration
 * This example doesn't require Temporal to be running
 */
public class SimpleMCPBedrockExample {

    public static void main(String[] args) {
        System.out.println("Simple AWS Bedrock + MCP Tools Integration Example");
        System.out.println("=================================================");

        // Initialize components
        System.out.println("Initializing Bedrock provider...");
        BedrockProvider bedrockProvider = new BedrockProvider();

        System.out.println("Initializing MCP tool integration...");
        MCPToolIntegration mcpIntegration = new MCPToolIntegration();

        // List available MCP tools
        System.out.println("\nAvailable MCP tools:");
        mcpIntegration.getAvailableTools()
                .forEach(tool -> System.out.println("- " + tool.name() + ": " + tool.description()));
        try {
            // Create chat messages
            ArrayList<MessageRecord> messages = new ArrayList<>();
            messages.add(new MessageRecord("user", "What's the weather like today?"));

            // Process with all available tools (local + MCP)
            MessageRecord response = bedrockProvider.chatWithTools(messages);

            System.out.println("Response: " + response.content());

        } catch (Exception e) {
            System.err.println("Error processing query: " + e.getMessage());
            mcpIntegration.close();
            System.exit(1);
        }

        // Clean up
        mcpIntegration.close();
        System.out.println("\nExample completed successfully!");

    }
}
