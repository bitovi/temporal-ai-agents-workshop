package bitovi.activities.a2a.types;

import java.util.HashMap;
import java.util.Map;

import bitovi.common.EventClient;

public record A2AMetadata(Map<String, Object> workflowMetadata, Map<String, Object> remoteToClientMetadata) {
    public static A2AMetadata fromPayload(A2APayload payload) {
        // Attempt to capture the WorkflowId from the Activity Context
        String capturedWorkflowId = null;
        try {
            capturedWorkflowId = io.temporal.activity.Activity.getExecutionContext().getInfo().getWorkflowId();
        } catch (Exception ignored) {
            // Its fine if we can't get the workflowId
        }

        Map<String, Object> workflowMetadata = new HashMap<>();
        workflowMetadata.put("agentName", payload.agentName());
        workflowMetadata.put("lane", EventClient.LANE_REMOTE);
        if (capturedWorkflowId != null) {
            workflowMetadata.put("workflowId", capturedWorkflowId);
        }

        Map<String, Object> remoteToClientMetadata = new HashMap<>(workflowMetadata);
        remoteToClientMetadata.put("targetLane", EventClient.LANE_CLIENT);

        return new A2AMetadata(workflowMetadata, remoteToClientMetadata);
    }
}
