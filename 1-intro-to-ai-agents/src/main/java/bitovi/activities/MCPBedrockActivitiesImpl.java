package bitovi.activities;

import java.util.ArrayList;
import java.util.List;

import bitovi.providers.BedrockProvider;
import bitovi.providers.LLMProviderException;
import bitovi.providers.MCPToolIntegration;
import bitovi.records.MessageRecord;
import io.temporal.activity.Activity;

public class MCPBedrockActivitiesImpl implements MCPBedrockActivities {
    
    private BedrockProvider bedrockProvider;
    private MCPToolIntegration mcpIntegration;
    
    public MCPBedrockActivitiesImpl() {
        this.bedrockProvider = new BedrockProvider();
        try {
            this.mcpIntegration = new MCPToolIntegration();
        } catch (Exception e) {
            System.err.println("Failed to initialize MCP integration: " + e.getMessage());
            this.mcpIntegration = null;
        }
    }
    
    @Override
    public String processWithMCPTools(String userQuery) {
        try {
            ArrayList<MessageRecord> messages = new ArrayList<>();
            messages.add(new MessageRecord("user", userQuery));
            
            // Use the enhanced chat method that includes MCP tools
            MessageRecord response = bedrockProvider.chatWithTools(messages);
            
            return response.content();
        } catch (LLMProviderException e) {
            throw Activity.wrap(e);
        }
    }
    
    @Override
    public List<String> getAvailableMCPTools() {
        if (mcpIntegration == null) {
            return new ArrayList<>();
        }
        
        return mcpIntegration.getAvailableTools().stream()
            .map(tool -> tool.name() + ": " + tool.description())
            .toList();
    }
    
    @Override
    public String executeSpecificMCPTool(String toolName, String input) {
        if (mcpIntegration == null) {
            return "MCP integration not available";
        }
        
        try {
            return mcpIntegration.executeMCPTool(toolName, 
                java.util.Map.of("input", input));
        } catch (Exception e) {
            return "Error executing tool: " + e.getMessage();
        }
    }
}
