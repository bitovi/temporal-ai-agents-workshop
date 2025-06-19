package bitovi.workflows.AgentGoal.activities;

import java.util.ArrayList;
import java.util.List;

import bitovi.common.DataTypes;
import bitovi.common.DataTypes.EnvLookupInputRecord;
import bitovi.common.DataTypes.EnvLookupOutputRecord;
import bitovi.common.DataTypes.MCPServerDefinitionRecord;
import bitovi.common.DataTypes.MessageRecord;
import bitovi.common.DataTypes.ToolPlannerResult;
import bitovi.common.DataTypes.ValidationInputRecord;
import bitovi.common.DataTypes.ValidationResultRecord;
import bitovi.workflows.AgentGoal.helpers.AgentToolPlannerResult;

public class AgentGoalsActivitiesImpl implements AgentGoalActivities {

    @Override
    public DataTypes.ValidationResultRecord validatePrompt() {
        return new DataTypes.ValidationResultRecord(true, "");
    }

    @Override
    public ValidationResultRecord validateUserInput(ValidationInputRecord input) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'validateUserInput'");
    }

    @Override
    public EnvLookupOutputRecord getWorkflowEnvSettings(EnvLookupInputRecord input) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getWorkflowEnvSettings'");
    }

    @Override
    public ListModelContextProtocolToolsResult listModelContextProtocolTools(
            MCPServerDefinitionRecord mcpServerDefinition, List<String> includeTools) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'listModelContextProtocolTools'");
    }

    @Override
    public ToolPlannerResult toolPlanner(String instructions, List<MessageRecord> history) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'toolPlanner'");
    }

    @Override
    public AgentToolPlannerResult agentToolPlanner(AgentToolPlannerInput input) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'agentToolPlanner'");
    }

    @Override
    public Object handleMissingArgs(String currentTool, Object args, Object toolData,
            ArrayList<String> promptQueue) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'handleMissingArgs'");
    }

}
