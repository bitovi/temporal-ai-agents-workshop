package bitovi.workflow;

import bitovi.workflow.types.MessagePayload;
import bitovi.workflow.types.WorkflowInput;
import bitovi.workflow.types.WorkflowResult;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface AgentWorkflow {
	@WorkflowMethod
	WorkflowResult execute(WorkflowInput input);

	@SignalMethod(name = "agentWorkflowMessage")
	void receiveMessage(MessagePayload payload);

	@SignalMethod(name = "agentWorkflowExit")
	void requestExit();

	@SignalMethod(name = "agentWorkflowCompact")
	void requestCompaction();
}