package bitovi.workflows.AgentGoal;

import java.util.ArrayList;
import java.util.List;

import bitovi.common.Agent;
import bitovi.common.DataTypes;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface AgentGoalWorkflow {

    @WorkflowMethod
    List<DataTypes.MessageRecord> run(DataTypes.CombinedWorkflowInput combinedInput);

    @SignalMethod
    void prompt(String prompt);

    @SignalMethod
    void disconnect();

    @SignalMethod
    void confirm();

    @QueryMethod
    ArrayList<DataTypes.MessageRecord> getConversationHistory();

    @QueryMethod
    Agent getAgentGoal();

    @QueryMethod
    String getConversationSummary();

    @QueryMethod
    DataTypes.ToolDataRecord getLatestToolData();
}
