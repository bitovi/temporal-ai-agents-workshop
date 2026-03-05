package bitovi.workflow;

import bitovi.workflow.types.MessagePayload;
import bitovi.workflow.types.WorkflowInput;
import bitovi.workflow.types.WorkflowResult;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface()
public interface AgentDecisionsReActWorkflow {
	@WorkflowMethod(name = "agentWorkflow")
	WorkflowResult execute(WorkflowInput input);

	@SignalMethod(name = "message")
	void receiveMessage(MessagePayload payload);

	@SignalMethod(name = "exit")
	void requestExit();

	@SignalMethod(name = "continueAsNew")
	void requestContinueAsNew();

	@QueryMethod(name = "getLastAnswer")
	String getAnswer();
}