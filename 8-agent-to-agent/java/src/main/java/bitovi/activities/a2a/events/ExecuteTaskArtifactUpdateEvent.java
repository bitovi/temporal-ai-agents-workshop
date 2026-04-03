package bitovi.activities.a2a.events;

import java.util.HashMap;
import java.util.Map;

import bitovi.activities.a2a.types.A2APayload;
import bitovi.common.EventClient;
import io.a2a.spec.Artifact;
import io.a2a.spec.DataPart;
import io.a2a.spec.Part;
import io.a2a.spec.TaskArtifactUpdateEvent;
import io.a2a.spec.TextPart;

public class ExecuteTaskArtifactUpdateEvent {
    public static void process(A2APayload payload, TaskArtifactUpdateEvent taue,
            Map<String, Object> remoteToClientMeta) {
        Artifact artifact = taue.getArtifact();
        System.out.println("[A2ATool:" + payload.agentName() + "] Artifact: " + artifact.name());

        Map<String, Object> artifactMap = new HashMap<>();
        artifactMap.put("title", artifact.name());

        if (artifact.parts() != null) {
            for (Part<?> part : artifact.parts()) {
                if (part instanceof DataPart dataPart) {
                    artifactMap.put("data", dataPart.getData());
                    handleDataPart(dataPart, artifact, remoteToClientMeta);
                } else if (part instanceof TextPart textPart) {
                    artifactMap.put("text", textPart.getText());
                    handleTextPart(textPart, artifact, remoteToClientMeta);
                }
            }
        }
        payload.collectedArtifacts().add(artifactMap);
    }

    private static void handleDataPart(DataPart dataPart, Artifact artifact, Map<String, Object> remoteToClientMeta) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("title", artifact.name());
        if (dataPart.getData() instanceof Map<?, ?> dataMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typedDataMap = (Map<String, Object>) dataMap;
            eventData.putAll(typedDataMap);
        }
        eventData.putAll(remoteToClientMeta);
        EventClient.emitEvent("a2a_artifact", artifact.name(), eventData);
    }

    private static void handleTextPart(TextPart textPart, Artifact artifact, Map<String, Object> remoteToClientMeta) {
        Map<String, Object> textArtData = new HashMap<>(
                Map.of("title", artifact.name()));
        textArtData.putAll(remoteToClientMeta);
        EventClient.emitEvent("a2a_artifact", textPart.getText(), textArtData);
    }
}
