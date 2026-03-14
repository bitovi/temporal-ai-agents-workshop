package bitovi.workflow;

import bitovi.workflow.types.MessagePayload;
import bitovi.workflow.types.WorkflowInput;
import bitovi.workflow.types.WorkflowResult;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Temporal workflow interface for the Agent-to-Agent exercise.
 *
 * Communication with the running workflow happens through:
 *   @SignalMethod — fire-and-forget messages INTO the workflow
 *   @QueryMethod  — read-only peek at workflow state (no side effects)
 *
 * The workflow runs indefinitely, processing user messages as they arrive
 * via the receiveMessage signal. The exit signal terminates it gracefully.
 */
@WorkflowInterface()
public interface AgentToAgentWorkflow {
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