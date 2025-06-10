package bitovi.activities;

import java.util.ArrayList;
import java.util.List;

import bitovi.providers.BedrockProvider;
import bitovi.providers.LLMProviderChatMessage;
import bitovi.providers.LLMProviderException;
import bitovi.providers.MCPToolIntegration;
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
            ArrayList<LLMProviderChatMessage> messages = new ArrayList<>();
            messages.add(new LLMProviderChatMessage("user", userQuery));
            
            // Use the enhanced chat method that includes MCP tools
            LLMProviderChatMessage response = bedrockProvider.chatWithAllTools(messages);
            
            return response.getContent();
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
