package bitovi.workflows.MCPBedrock;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface MCPBedrockWorkflow {
    
    @WorkflowMethod
    String processQueryWithMCPTools(String userQuery);
}
