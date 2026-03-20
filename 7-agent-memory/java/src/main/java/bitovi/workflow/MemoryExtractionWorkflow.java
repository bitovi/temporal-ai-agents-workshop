package bitovi.workflow;

import bitovi.workflow.types.MemoryExtractionEventInput;
import bitovi.workflow.types.MemoryExtractionWorkflowInput;
import bitovi.workflow.types.WorkflowResult;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface MemoryExtractionWorkflow {
	@WorkflowMethod(name = "memoryExtractionWorkflow")
	WorkflowResult execute(MemoryExtractionWorkflowInput input);

	@SignalMethod(name = "event")
	void receiveMessage(MemoryExtractionEventInput event);
}