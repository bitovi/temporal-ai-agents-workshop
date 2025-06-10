package bitovi.examples;

import java.util.Map;

import bitovi.providers.BedrockProvider;
import bitovi.providers.MCPToolIntegration;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;

/**
 * Example showing how to extend the MCP-Bedrock integration with custom tools
 */
public class CustomToolExample {
    
    public static void main(String[] args) {
        System.out.println("Custom Tool Integration Example");
        System.out.println("===============================");
        
        try {
            // Example 1: Create a custom local tool
            Tool customTool = createCustomTool();
            System.out.println("Created custom tool: " + customTool.toolSpec().name());
            
            // Example 2: Use the integration with custom queries
            demonstrateCustomQueries();
            
            // Example 3: Show tool discovery and listing
            demonstrateToolDiscovery();
            
        } catch (Exception e) {
            System.err.println("Error in custom tool example: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Example: Create a custom local tool for currency conversion
     */
    public static Tool createCustomTool() {
        String schema = "{\n" +
            "    \"type\": \"object\",\n" +
            "    \"properties\": {\n" +
            "        \"amount\": {\n" +
            "            \"type\": \"number\",\n" +
            "            \"description\": \"Amount to convert\"\n" +
            "        },\n" +
            "        \"from_currency\": {\n" +
            "            \"type\": \"string\",\n" +
            "            \"description\": \"Source currency code (USD, EUR, etc.)\"\n" +
            "        },\n" +
            "        \"to_currency\": {\n" +
            "            \"type\": \"string\",\n" +
            "            \"description\": \"Target currency code (USD, EUR, etc.)\"\n" +
            "        }\n" +
            "    },\n" +
            "    \"required\": [\"amount\", \"from_currency\", \"to_currency\"]\n" +
            "}";
        
        return Tool.builder()
            .toolSpec(ToolSpecification.builder()
                .name("convert_currency")
                .description("Convert amount between different currencies")
                .inputSchema(ToolInputSchema.builder()
                    .json(Document.fromString(schema))
                    .build())
                .build())
            .build();
    }
    
    /**
     * Example: Execute the custom currency conversion tool
     */
    public static String executeCurrencyConversion(Map<String, Object> args) {
        // In a real implementation, this would call a currency API
        // For demo purposes, we'll use a simple mock conversion
        
        double amount = Double.parseDouble(args.get("amount").toString());
        String fromCurrency = args.get("from_currency").toString();
        String toCurrency = args.get("to_currency").toString();
        
        // Mock conversion rate (in reality, fetch from API)
        double rate = getMockExchangeRate(fromCurrency, toCurrency);
        double convertedAmount = amount * rate;
        
        return String.format("%.2f %s = %.2f %s (rate: %.4f)", 
            amount, fromCurrency, convertedAmount, toCurrency, rate);
    }
    
    private static double getMockExchangeRate(String from, String to) {
        // Mock exchange rates for demonstration
        if (from.equals("USD") && to.equals("EUR")) return 0.85;
        if (from.equals("EUR") && to.equals("USD")) return 1.18;
        if (from.equals("USD") && to.equals("GBP")) return 0.73;
        if (from.equals("GBP") && to.equals("USD")) return 1.37;
        return 1.0; // Default to 1:1 for unknown pairs
    }
    
    /**
     * Demonstrate custom queries that use multiple tool types
     */
    private static void demonstrateCustomQueries() {
        System.out.println("\n--- Custom Query Examples ---");
        
        String[] queries = {
            "Convert 100 USD to EUR and calculate the cosine of the result",
            "What's the weather in London and convert 50 GBP to USD",
            "Calculate cosine of 1.57 and tell me the current time"
        };
        
        for (String query : queries) {
            System.out.println("\nQuery: " + query);
            System.out.println("Expected tools: Multiple (local + MCP + custom)");
            // Note: Full execution would require complete integration
        }
    }
    
    /**
     * Demonstrate tool discovery and listing capabilities
     */
    private static void demonstrateToolDiscovery() {
        System.out.println("\n--- Tool Discovery Example ---");
        
        try {
            // Initialize MCP integration
            MCPToolIntegration mcpIntegration = new MCPToolIntegration();
            
            System.out.println("Available MCP Tools:");
            mcpIntegration.getAvailableTools().forEach(tool -> 
                System.out.println("  • " + tool.name() + ": " + tool.description())
            );
            
            System.out.println("\nLocal Tools:");
            System.out.println("  • calculate_cosine: Calculate cosine of a number in radians");
            System.out.println("  • convert_currency: Convert between currencies (custom example)");
            
            // Get Bedrock-compatible specifications
            System.out.println("\nBedrock Tool Specifications:");
            mcpIntegration.getBedrockToolSpecifications().forEach(tool ->
                System.out.println("  • " + tool.toolSpec().name())
            );
            
            mcpIntegration.close();
            
        } catch (Exception e) {
            System.err.println("Tool discovery failed: " + e.getMessage());
        }
    }
}

/**
 * Helper class for extending BedrockProvider with custom tools
 */
class ExtendedBedrockProvider extends BedrockProvider {
    
    public ExtendedBedrockProvider() {
        super();
    }
    
    /**
     * Override to add custom tool handling
     */
    // Note: This would require modifying the converseWithToolsRecursive method
    // to include custom tool execution logic
    
    /**
     * Example of how to add a custom tool to the conversation
     */
    public void addCustomToolSupport() {
        // This would integrate with the existing tool execution flow
        // Adding custom tools to the available tool list
        System.out.println("Custom tool support would be added here");
    }
}
