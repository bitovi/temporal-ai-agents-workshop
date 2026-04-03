package bitovi.activities.a2a.events;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;

import bitovi.activities.a2a.A2AHelpers;
import bitovi.activities.a2a.types.A2APayload;
import bitovi.common.EventClient;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatusUpdateEvent;

public class ExecuteTaskStatusUpdateEvent {
    private static final Gson gson = new Gson();

    private record InputRequiredData(String taskId, String contextId, Map<String, Object> remoteToClientMeta) {

        public static InputRequiredData fromTaskStatusUpdateEvent(TaskStatusUpdateEvent tsue,
                Map<String, Object> remoteToClientMeta) {
            return new InputRequiredData(tsue.getTaskId(), tsue.getContextId(), remoteToClientMeta);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> irData = new HashMap<>();
            irData.put("taskId", this.taskId);
            irData.put("contextId", this.contextId);
            irData.putAll(remoteToClientMeta);
            return irData;
        }

        public String toJSON(String statusMsg) {
            Map<String, Object> result = new HashMap<>();
            result.put("status", "input_required");
            result.put("taskId", this.taskId);
            result.put("contextId", this.contextId);
            result.put("message", statusMsg);
            return gson.toJson(result);
        }

    }

    public static void process(A2APayload payload, TaskStatusUpdateEvent tsue, Map<String, Object> workflowMetadata,
            Map<String, Object> remoteToClientMeta) {
        TaskState state = tsue.getStatus().state();
        String statusMsg = A2AHelpers.extractTextFromMessage(tsue.getStatus().message());
        System.out.println(
                "[A2ATool:" + payload.agentName() + "] Status: " + state + " — " + statusMsg);

        switch (state) {
            case WORKING -> EventClient.emitEvent("a2a_working", statusMsg, workflowMetadata);

            case INPUT_REQUIRED -> {
                InputRequiredData ird = InputRequiredData.fromTaskStatusUpdateEvent(tsue, remoteToClientMeta);
                EventClient.emitEvent("a2a_input_required", statusMsg, ird.toMap());
                payload.resultJsonRef().set(ird.toJSON(statusMsg));
                payload.latch().countDown();
            }

            case COMPLETED -> {
                EventClient.emitEvent("a2a_completed", A2AHelpers.cleanCompletedSummary(statusMsg),
                        new HashMap<>(remoteToClientMeta));
                Map<String, Object> result = new HashMap<>();
                result.put("status", "completed");
                result.put("message", statusMsg);
                if (!payload.collectedArtifacts().isEmpty()) {
                    result.put("artifacts", payload.collectedArtifacts());
                }
                payload.resultJsonRef().set(gson.toJson(result));
                payload.latch().countDown();
            }

            case FAILED -> {
                EventClient.emitEvent("a2a_failed", statusMsg, new HashMap<>(remoteToClientMeta));
                Map<String, Object> result = new HashMap<>();
                result.put("status", "failed");
                result.put("message", statusMsg);
                payload.resultJsonRef().set(gson.toJson(result));
                payload.latch().countDown();
            }

            default -> {
                EventClient.emitEvent("a2a_unknown", statusMsg, new HashMap<>(remoteToClientMeta));
                Map<String, Object> result = new HashMap<>();
                result.put("status", "unknown");
                result.put("message", statusMsg);
                payload.resultJsonRef().set(gson.toJson(result));
                payload.latch().countDown();
            }
        }
    }
}
