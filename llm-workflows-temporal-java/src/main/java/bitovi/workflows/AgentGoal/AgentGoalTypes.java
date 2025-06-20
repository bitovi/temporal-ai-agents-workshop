package bitovi.workflows.AgentGoal;

import java.util.ArrayList;

public interface AgentGoalTypes {
    record AgentGoalWorkflowInput(
            String goal,
            String[] tools,
            String[] toolArgs,
            String[] toolDescriptions,
            String[] toolNames) {
        // This record class encapsulates the input parameters for the
        // AgentGoalWorkflow.
    }

    record AgentGoalWorkflowOutput(
            String goal,
            String[] tools,
            String[] toolArgs,
            String[] toolDescriptions,
            String[] toolNames) {
        // This record class encapsulates the output parameters for the
        // AgentGoalWorkflow.
    }

    record AgentGoalConversationHistory(ArrayList<AgentGoalConversationEntry> messages) {
        // This record class encapsulates the conversation history for the
        // AgentGoalWorkflow.
        @Override
        public final String toString() {
            StringBuilder sb = new StringBuilder();
            for (AgentGoalConversationEntry entry : messages) {
                sb.append(entry.toString()).append("\n");
            }
            return sb.toString();
        }
    }

    record AgentGoalConversationEntry(String actor, String response) {
        // This record class encapsulates a single entry in the conversation history.
        @Override
        public final String toString() {
            return actor + ": " + response;
        }
    }
}
