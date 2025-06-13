package bitovi.workflows.AgentGoal;

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
}
