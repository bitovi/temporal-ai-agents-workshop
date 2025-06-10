package bitovi.workflows.MCPBedrock;

import java.util.List;

import bitovi.Config;
import bitovi.activities.MCPBedrockActivities;
import io.temporal.workflow.Workflow;

public class MCPBedrockWorkflowImpl implements MCPBedrockWorkflow {
    
    private final MCPBedrockActivities activities = Workflow.newActivityStub(
        MCPBedrockActivities.class,
        Config.getDefaultActivityOptions()
    );
    
    @Override
    public String processQueryWithMCPTools(String userQuery) {
        // First, list available MCP tools for logging
        List<String> availableTools = activities.getAvailableMCPTools();
        
        Workflow.getLogger("MCPBedrockWorkflow").info(
            "Processing query with " + availableTools.size() + " MCP tools available"
        );
        
        // Log available tools
        for (String tool : availableTools) {
            Workflow.getLogger("MCPBedrockWorkflow").info("Available tool: " + tool);
        }
        
        // Process the query using Bedrock with MCP tools
        String response = activities.processWithMCPTools(userQuery);
        
        Workflow.getLogger("MCPBedrockWorkflow").info("Query processed successfully");
        
        return response;
    }
}
