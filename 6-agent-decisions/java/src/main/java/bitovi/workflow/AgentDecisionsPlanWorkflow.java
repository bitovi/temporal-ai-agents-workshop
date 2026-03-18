package bitovi.workflow;

import bitovi.workflow.types.PlanWorkflowInput;
import bitovi.workflow.types.PlanWorkflowResult;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface()
public interface AgentDecisionsPlanWorkflow {
	@WorkflowMethod(name = "agentPlanWorkflow")
	PlanWorkflowResult execute(PlanWorkflowInput payload);
}