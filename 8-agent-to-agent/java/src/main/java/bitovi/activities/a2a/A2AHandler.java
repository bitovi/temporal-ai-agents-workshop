package bitovi.activities.a2a;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.google.gson.Gson;

import bitovi.activities.a2a.A2ARegistry.AgentConnection;
import bitovi.common.EventClient;
import io.a2a.client.ClientEvent;
import io.a2a.client.MessageEvent;
import io.a2a.client.TaskEvent;
import io.a2a.client.TaskUpdateEvent;
import io.a2a.spec.AgentCard;
import io.a2a.spec.Artifact;
import io.a2a.spec.DataPart;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.TaskArtifactUpdateEvent;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatusUpdateEvent;
import io.a2a.spec.TextPart;
import io.a2a.spec.UpdateEvent;

public class A2AHandler {
    private static final int TIMEOUT_SECONDS = 120;
    private static final Gson gson = new Gson();

    public static String sendAndCollect(AgentConnection conn, Message message) throws InterruptedException {
        StringBuilder responseBuilder = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> errorRef = new AtomicReference<>();
        AtomicReference<String> resultJsonRef = new AtomicReference<>();
        List<Map<String, Object>> collectedArtifacts = new ArrayList<>();
        String agentName = conn.card().name();

        // Capture workflowId on the activity thread — streaming callbacks run on the
        // A2A SDK's own threads where Activity.getExecutionContext() is not available.
        String capturedWorkflowId = null;
        try {
            capturedWorkflowId = io.temporal.activity.Activity.getExecutionContext().getInfo().getWorkflowId();
        } catch (Exception ignored) {
        }
        // Base metadata for remote-lane events (working, completed, failed, artifact)
        final Map<String, Object> wfMeta;
        {
            Map<String, Object> meta = new HashMap<>();
            meta.put("agentName", agentName);
            meta.put("lane", EventClient.LANE_REMOTE);
            if (capturedWorkflowId != null)
                meta.put("workflowId", capturedWorkflowId);
            wfMeta = meta;
        }
        // Metadata for events that go remote → client (input_required, completed with
        // result)
        final Map<String, Object> remoteToClientMeta;
        {
            Map<String, Object> meta = new HashMap<>(wfMeta);
            meta.put("targetLane", EventClient.LANE_CLIENT);
            remoteToClientMeta = meta;
        }

        List<BiConsumer<ClientEvent, AgentCard>> consumers = List.of(
                (event, card) -> {
                    try {
                        if (event instanceof TaskUpdateEvent tue) {
                            UpdateEvent ue = tue.getUpdateEvent();

                            if (ue instanceof TaskStatusUpdateEvent tsue) {
                                TaskState state = tsue.getStatus().state();
                                String statusMsg = A2AHelpers.extractTextFromMessage(tsue.getStatus().message());
                                System.out.println("[A2ATool:" + agentName + "] Status: " + state + " — " + statusMsg);

                                if (state == TaskState.WORKING) {
                                    EventClient.emitEvent("a2a_working", statusMsg, wfMeta);

                                } else if (state == TaskState.INPUT_REQUIRED) {
                                    String returnTaskId = tsue.getTaskId();
                                    String returnContextId = tsue.getContextId();

                                    Map<String, Object> irData = new HashMap<>();
                                    irData.put("taskId", returnTaskId);
                                    irData.put("contextId", returnContextId);
                                    irData.putAll(remoteToClientMeta);
                                    EventClient.emitEvent("a2a_input_required", statusMsg, irData);

                                    Map<String, Object> result = new HashMap<>();
                                    result.put("status", "input_required");
                                    result.put("taskId", returnTaskId);
                                    result.put("contextId", returnContextId);
                                    result.put("message", statusMsg);
                                    resultJsonRef.set(gson.toJson(result));
                                    latch.countDown();

                                } else if (state == TaskState.COMPLETED) {
                                    EventClient.emitEvent("a2a_completed", A2AHelpers.cleanCompletedSummary(statusMsg),
                                            new HashMap<>(remoteToClientMeta));

                                    Map<String, Object> result = new HashMap<>();
                                    result.put("status", "completed");
                                    result.put("message", statusMsg);
                                    if (!collectedArtifacts.isEmpty()) {
                                        result.put("artifacts", collectedArtifacts);
                                    }
                                    resultJsonRef.set(gson.toJson(result));
                                    latch.countDown();

                                } else if (state == TaskState.FAILED) {
                                    EventClient.emitEvent("a2a_failed", statusMsg, new HashMap<>(remoteToClientMeta));

                                    Map<String, Object> result = new HashMap<>();
                                    result.put("status", "failed");
                                    result.put("message", statusMsg);
                                    resultJsonRef.set(gson.toJson(result));
                                    latch.countDown();
                                } else {
                                    EventClient.emitEvent("a2a_unknown", statusMsg, new HashMap<>(remoteToClientMeta));

                                    Map<String, Object> result = new HashMap<>();
                                    result.put("status", "unknown");
                                    result.put("message", statusMsg);
                                    resultJsonRef.set(gson.toJson(result));
                                    latch.countDown();
                                }

                            } else if (ue instanceof TaskArtifactUpdateEvent taue) {
                                Artifact artifact = taue.getArtifact();
                                System.out.println("[A2ATool:" + agentName + "] Artifact: " + artifact.name());

                                Map<String, Object> artifactMap = new HashMap<>();
                                artifactMap.put("title", artifact.name());

                                if (artifact.parts() != null) {
                                    for (Part<?> part : artifact.parts()) {
                                        if (part instanceof DataPart dataPart) {
                                            artifactMap.put("data", dataPart.getData());
                                            Map<String, Object> eventData = new HashMap<>();
                                            eventData.put("title", artifact.name());
                                            if (dataPart.getData() instanceof Map<?, ?> dataMap) {
                                                @SuppressWarnings("unchecked")
                                                Map<String, Object> typedDataMap = (Map<String, Object>) dataMap;
                                                eventData.putAll(typedDataMap);
                                            }
                                            eventData.putAll(remoteToClientMeta);
                                            EventClient.emitEvent("a2a_artifact", artifact.name(), eventData);
                                        } else if (part instanceof TextPart textPart) {
                                            artifactMap.put("text", textPart.getText());
                                            Map<String, Object> textArtData = new HashMap<>(
                                                    Map.of("title", artifact.name()));
                                            textArtData.putAll(remoteToClientMeta);
                                            EventClient.emitEvent("a2a_artifact", textPart.getText(), textArtData);
                                        }
                                    }
                                }

                                collectedArtifacts.add(artifactMap);
                            }

                        } else if (event instanceof MessageEvent messageEvent) {
                            Message msg = messageEvent.getMessage();
                            String text = A2AHelpers.extractTextFromParts(msg.getParts());
                            if (!text.isEmpty()) {
                                responseBuilder.append(text);
                                System.out.println("[A2ATool:" + agentName + "] Message: " + text);

                                if (resultJsonRef.get() == null) {
                                    Map<String, Object> result = new HashMap<>();
                                    result.put("status", "completed");
                                    result.put("message", text);
                                    if (!collectedArtifacts.isEmpty()) {
                                        result.put("artifacts", collectedArtifacts);
                                    }
                                    resultJsonRef.set(gson.toJson(result));
                                }
                                latch.countDown();
                            }

                        } else if (event instanceof TaskEvent taskEvent) {
                            System.out.println("[A2ATool:" + agentName + "] TaskEvent received");
                            io.a2a.spec.Task task = taskEvent.getTask();
                            TaskState taskState = task.getStatus() != null ? task.getStatus().state()
                                    : TaskState.COMPLETED;
                            String stateStr = taskState.name().toLowerCase().replace("task_state_", "").replace('_',
                                    '-');

                            if (resultJsonRef.get() == null) {
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

                                if (!collectedArtifacts.isEmpty()) {
                                    result.put("artifacts", collectedArtifacts);
                                }
                                resultJsonRef.set(gson.toJson(result));
                            }
                            // Only count down latch for terminal states — not for 'working'
                            if (taskState != TaskState.WORKING) {
                                latch.countDown();
                            }
                        }

                    } catch (Exception e) {
                        System.err.println("[A2ATool:" + agentName + "] Error processing event: " + e.getMessage());
                        e.printStackTrace();
                        errorRef.set(e.getMessage());
                        latch.countDown();
                    }
                });

        Consumer<Throwable> errorHandler = error -> {
            if (error == null) {
                return;
            }
            System.err.println("[A2ATool:" + agentName + "] Error: " + error.getMessage());
            errorRef.set(error.getMessage());
            latch.countDown();
        };

        System.out.println("[A2ATool:" + agentName + "] Sending message...");
        conn.client().sendMessage(message, consumers, errorHandler);

        boolean completed = latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (!completed) {
            String timeoutMsg = "Request timed out after " + TIMEOUT_SECONDS + " seconds.";
            System.err.println("[A2ATool:" + agentName + "] " + timeoutMsg);
            return gson.toJson(Map.of("status", "failed", "message", timeoutMsg));
        }

        // Check resultJsonRef FIRST — the event callbacks set it on terminal
        // states (COMPLETED, FAILED, INPUT_REQUIRED). A transport-level error
        // like "Request cancelled" can arrive after the result is already
        // captured, so a valid result always takes priority over errorRef.
        String resultJson = resultJsonRef.get();
        if (resultJson != null) {
            System.out.println("[A2ATool:" + agentName + "] Result: " + resultJson);
            return resultJson;
        }

        String error = errorRef.get();
        if (error != null) {
            return gson.toJson(Map.of("status", "failed", "message", "Error: " + error));
        }

        String text = responseBuilder.toString();
        if (!text.isEmpty()) {
            return gson.toJson(Map.of("status", "completed", "message", text));
        }

        return gson.toJson(Map.of("status", "failed", "message", "No response received from " + agentName));
    }
}
