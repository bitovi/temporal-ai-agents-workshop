package bitovi.activities.a2a.types;

public record A2ARequestInput(String agentUrl, String message, String taskId, String contextId, boolean existingTask) {

    public A2ARequestInput(String agentUrl, String message, String taskId, String contextId) {
        this(agentUrl, message, taskId, contextId, taskId != null && contextId != null);
    }
}
