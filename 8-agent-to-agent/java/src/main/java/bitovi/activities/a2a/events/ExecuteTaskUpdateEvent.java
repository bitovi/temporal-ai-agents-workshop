package bitovi.activities.a2a.events;

import java.util.Map;

import bitovi.activities.a2a.types.A2AMetadata;
import bitovi.activities.a2a.types.A2APayload;
import io.a2a.client.TaskUpdateEvent;
import io.a2a.spec.TaskArtifactUpdateEvent;
import io.a2a.spec.TaskStatusUpdateEvent;
import io.a2a.spec.UpdateEvent;

public class ExecuteTaskUpdateEvent {
    public static void process(A2APayload payload, TaskUpdateEvent tue) {
        A2AMetadata metadata = A2AMetadata.fromPayload(payload);

        Map<String, Object> workflowMetadata = metadata.workflowMetadata();
        Map<String, Object> remoteToClientMeta = metadata.remoteToClientMetadata();

        UpdateEvent ue = tue.getUpdateEvent();

        switch (ue) {
            case TaskStatusUpdateEvent tsue -> {
                ExecuteTaskStatusUpdateEvent.process(payload, tsue, workflowMetadata, remoteToClientMeta);
            }

            case TaskArtifactUpdateEvent taue -> {
                ExecuteTaskArtifactUpdateEvent.process(payload, taue, remoteToClientMeta);
            }

            default -> {
            }
        }
    }
}
