package bitovi.workflows.AgentGoal;

import java.util.List;

import bitovi.common.Agent;
import bitovi.workflows.AgentGoal.AgentGoalTypes.AgentGoalConversationHistory;
import bitovi.workflows.AgentGoal.helpers.AgentToolPlannerResult;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface AgentGoalWorkflow {

    @WorkflowMethod
    AgentGoalConversationHistory run(CombinedWorkflowInput combinedInput);

    @SignalMethod
    void prompt(String prompt);

    @SignalMethod
    void disconnect();

    @SignalMethod
    void confirm();

    @QueryMethod
    AgentGoalConversationHistory getConversationHistory();

    @QueryMethod
    Agent getAgentGoal();

    @QueryMethod
    String getConversationSummary();

    @QueryMethod
    AgentToolPlannerResult getLatestToolData();

    public record CombinedWorkflowInput(AgentGoalWorkflowParams toolParams, Agent agentGoal) {

    }

    public record AgentGoalWorkflowParams(String conversationSummary, List<String> promptQueue) {

    }
}
