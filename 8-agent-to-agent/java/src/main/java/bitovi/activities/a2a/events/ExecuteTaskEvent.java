package bitovi.activities.a2a.events;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;

import bitovi.activities.a2a.A2AHelpers;
import bitovi.activities.a2a.types.A2AMetadata;
import bitovi.activities.a2a.types.A2APayload;
import bitovi.common.EventClient;
import io.a2a.client.TaskEvent;
import io.a2a.spec.Task;
import io.a2a.spec.TaskState;

public class ExecuteTaskEvent {
    private static final Gson gson = new Gson();

    public static void process(A2APayload payload, TaskEvent taskEvent) {
        A2AMetadata metadata = A2AMetadata.fromPayload(payload);
        Map<String, Object> wfMeta = metadata.workflowMetadata();
        Map<String, Object> remoteToClientMeta = metadata.remoteToClientMetadata();

        System.out.println("[A2ATool:" + payload.agentName() + "] TaskEvent received");
        Task task = taskEvent.getTask();
        TaskState taskState = task.getStatus() != null ? task.getStatus().state()
                : TaskState.COMPLETED;
        String stateStr = taskState.name().toLowerCase().replace("task_state_", "").replace('_',
                '-');

        if (payload.resultJsonRef().get() == null) {
            String text = "";
            if (task.getStatus() != null && task.getStatus().message() != null) {
                text = A2AHelpers.extractTextFromMessage(task.getStatus().message());
            }
            Map<String, Object> result = new HashMap<>();
            result.put("status", stateStr);
            result.put("message", text);

            if (taskState == TaskState.INPUT_REQUIRED) {
                result.put("taskId", task.getId());
                result.put("contextId", task.getContextId());
                Map<String, Object> irEvtData = new HashMap<>();
                irEvtData.put("taskId", task.getId());
                irEvtData.put("contextId", task.getContextId());
                irEvtData.putAll(remoteToClientMeta);
                EventClient.emitEvent("a2a_input_required", text, irEvtData);
            } else if (taskState == TaskState.WORKING) {
                EventClient.emitEvent("a2a_working", text, new HashMap<>(wfMeta));
            } else if (taskState == TaskState.COMPLETED) {
                EventClient.emitEvent("a2a_completed", A2AHelpers.cleanCompletedSummary(text),
                        new HashMap<>(remoteToClientMeta));
            } else if (taskState == TaskState.FAILED) {
                EventClient.emitEvent("a2a_failed", text, new HashMap<>(remoteToClientMeta));
            }

            if (!payload.collectedArtifacts().isEmpty()) {
                result.put("artifacts", payload.collectedArtifacts());
            }
            payload.resultJsonRef().set(gson.toJson(result));
        }
        // Only count down latch for terminal states — not for 'working'
        if (taskState != TaskState.WORKING) {
            payload.latch().countDown();
        }
    }
}
