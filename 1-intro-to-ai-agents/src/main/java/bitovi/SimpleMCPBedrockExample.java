package bitovi;

import java.util.ArrayList;

import bitovi.providers.BedrockProvider;
import bitovi.providers.LLMProviderChatMessage;
import bitovi.providers.MCPToolIntegration;

/**
 * Simple standalone example of AWS Bedrock + MCP Tools integration
 * This example doesn't require Temporal to be running
 */
public class SimpleMCPBedrockExample {
    
    public static void main(String[] args) {
        System.out.println("Simple AWS Bedrock + MCP Tools Integration Example");
        System.out.println("=================================================");
        
        try {
            // Initialize components
            System.out.println("Initializing Bedrock provider...");
            BedrockProvider bedrockProvider = new BedrockProvider();
            
            System.out.println("Initializing MCP tool integration...");
            MCPToolIntegration mcpIntegration = new MCPToolIntegration();
            
            // List available MCP tools
            System.out.println("\nAvailable MCP tools:");
            mcpIntegration.getAvailableTools().forEach(tool -> 
                System.out.println("- " + tool.name() + ": " + tool.description())
            );
            
            // Test queries
            String[] testQueries = {
                "Calculate the cosine of 1.57 radians",
                "What's the weather like today?",
                "Hello, how are you?"
            };
            
            for (String query : testQueries) {
                System.out.println("\n--- Processing Query ---");
                System.out.println("Query: " + query);
                
                try {
                    // Create chat messages
                    ArrayList<LLMProviderChatMessage> messages = new ArrayList<>();
                    messages.add(new LLMProviderChatMessage("user", query));
                    
                    // Process with all available tools (local + MCP)
                    LLMProviderChatMessage response = bedrockProvider.chatWithAllTools(messages);
                    
                    System.out.println("Response: " + response.getContent());
                    
                } catch (Exception e) {
                    System.err.println("Error processing query: " + e.getMessage());
                }
            }
            
            // Clean up
            mcpIntegration.close();
            System.out.println("\nExample completed successfully!");
            
        } catch (Exception e) {
            System.err.println("Failed to initialize components: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
