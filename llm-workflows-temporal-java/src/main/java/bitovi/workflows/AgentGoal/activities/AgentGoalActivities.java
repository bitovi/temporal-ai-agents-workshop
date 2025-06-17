package bitovi.workflows.AgentGoal.activities;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import bitovi.common.Agent;
import bitovi.common.DataTypes;
import bitovi.common.DataTypes.MessageRecord;
import bitovi.common.DataTypes.ToolDataRecord;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface AgentGoalActivities {

        @ActivityMethod
        public DataTypes.ValidationResultRecord validateUserInput(DataTypes.ValidationInputRecord input);

        @ActivityMethod
        public DataTypes.ValidationResultRecord validatePrompt();

        @ActivityMethod
        public String generateGenAIPrompt(DataTypes.MessageRecord userInput,
                        ArrayList<DataTypes.MessageRecord> history,
                        Agent currentGoal);

        @ActivityMethod
        public DataTypes.ToolPlannerResult toolPlanner(String instructions, List<MessageRecord> history);

        @ActivityMethod
        public AgentToolPlannerResult agentToolPlanner(String prompt, String contextInstructions);

        public record AgentToolPlannerResult(Map<String, String> structuredOutput) {

        }

        @ActivityMethod
        public DataTypes.EnvLookupOutputRecord getWorkflowEnvSettings(DataTypes.EnvLookupInputRecord input);

        @ActivityMethod
        public DataTypes.ListModelContextProtocolToolsResult listModelContextProtocolTools(
                        DataTypes.MCPServerDefinitionRecord mcpServerDefinition, List<String> includeTools);

        public Object handleMissingArgs(String currentTool, String args, ToolDataRecord toolData,
                        ArrayList<MessageRecord> promptQueue);
}