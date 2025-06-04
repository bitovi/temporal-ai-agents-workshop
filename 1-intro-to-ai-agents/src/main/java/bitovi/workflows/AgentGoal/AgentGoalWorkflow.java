package bitovi.workflows.AgentGoal;

import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface AgentGoalWorkflow {

    @WorkflowMethod
    void run();

    @SignalMethod
    void prompt(String prompt);

    @SignalMethod
    void disconnect();
}
