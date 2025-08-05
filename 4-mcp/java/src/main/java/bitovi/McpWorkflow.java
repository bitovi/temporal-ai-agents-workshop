package bitovi;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface McpWorkflow {

	@WorkflowMethod
	String execute();
}