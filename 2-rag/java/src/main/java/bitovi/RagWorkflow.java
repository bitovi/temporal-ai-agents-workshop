package bitovi;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface RagWorkflow {

	@WorkflowMethod
	String execute(String searchTerm);
}