package bitovi;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface AgentWorkflow {
	record ActionWorkflowInput(String query, List<String> context) {
	}

	@WorkflowMethod
	String execute(ActionWorkflowInput input);
}