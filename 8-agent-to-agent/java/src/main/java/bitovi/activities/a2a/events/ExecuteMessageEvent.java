package bitovi.activities.a2a.events;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;

import bitovi.activities.a2a.A2AHelpers;
import bitovi.activities.a2a.types.A2APayload;
import io.a2a.client.MessageEvent;
import io.a2a.spec.Message;

public class ExecuteMessageEvent {
    private static final Gson gson = new Gson();

    public static void process(A2APayload payload, MessageEvent messageEvent) {
        Message msg = messageEvent.getMessage();
        String text = A2AHelpers.extractTextFromParts(msg.getParts());

        if (!text.isEmpty()) {
            payload.responseBuilder().append(text);
            System.out.println("[A2ATool:" + payload.agentName() + "] Message: " + text);

            if (payload.resultJsonRef().get() == null) {
                Map<String, Object> result = new HashMap<>();
                result.put("status", "completed");
                result.put("message", text);
                if (!payload.collectedArtifacts().isEmpty()) {
                    result.put("artifacts", payload.collectedArtifacts());
                }
                payload.resultJsonRef().set(gson.toJson(result));
            }

            payload.latch().countDown();
        }
    }
}
