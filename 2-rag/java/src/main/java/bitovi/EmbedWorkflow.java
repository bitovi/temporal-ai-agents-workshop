package bitovi;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface EmbedWorkflow {

	@WorkflowMethod
	void execute(String[] urls);
}