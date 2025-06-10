package bitovi.activities;

import java.util.List;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface MCPBedrockActivities {
    
    @ActivityMethod
    public String processWithMCPTools(String userQuery);
    
    @ActivityMethod
    public List<String> getAvailableMCPTools();
    
    @ActivityMethod
    public String executeSpecificMCPTool(String toolName, String input);
}
