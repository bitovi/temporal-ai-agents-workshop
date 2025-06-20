package bitovi.workflows.AgentGoal.activities;

import java.util.ArrayList;
import java.util.List;

import bitovi.common.DataTypes;
import bitovi.workflows.AgentGoal.helpers.AgentToolDefinition;
import bitovi.workflows.AgentGoal.helpers.AgentToolPlannerResult;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface AgentGoalActivities {

        @ActivityMethod
        public DataTypes.ValidationResultRecord validateUserInput(DataTypes.ValidationInputRecord input);

        @ActivityMethod
        public AgentToolPlannerResult agentToolPlanner(AgentToolPlannerInput input);

        @ActivityMethod
        public DataTypes.EnvLookupOutputRecord getWorkflowEnvSettings(DataTypes.EnvLookupInputRecord input);

        @ActivityMethod
        public ListModelContextProtocolToolsResult listModelContextProtocolTools(
                        DataTypes.MCPServerDefinitionRecord mcpServerDefinition, List<String> includeTools);

        @ActivityMethod
        public Object handleMissingArgs(String currentTool, Object args, Object toolData,
                        ArrayList<String> promptQueue);

        public record AgentToolPlannerInput(String prompt, String contextInstructions) {

        }

        public record ListModelContextProtocolToolsResult(boolean success, String error,
                        ArrayList<AgentToolDefinition> tools) {
        }
}