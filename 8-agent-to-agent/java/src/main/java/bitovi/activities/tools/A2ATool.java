package bitovi.activities.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.google.gson.Gson;

import io.a2a.A2A;
import io.a2a.client.Client;
import io.a2a.client.ClientEvent;
import io.a2a.client.MessageEvent;
import io.a2a.client.TaskEvent;
import io.a2a.client.TaskUpdateEvent;
import io.a2a.client.config.ClientConfig;
import io.a2a.client.http.A2ACardResolver;
import io.a2a.client.transport.jsonrpc.JSONRPCTransport;
import io.a2a.client.transport.jsonrpc.JSONRPCTransportConfig;
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
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;

import bitovi.common.EventClient;

/**
 * Generic A2A (Agent-to-Agent) communication tool.
 *
 * The LLM calls this with an agentUrl (discovered via search_agent_registry)
 * and a message. The tool handles agent card resolution, client creation,
 * and the full streaming A2A protocol. Clients are cached per URL.
 */
public class A2ATool {
    private static final int TIMEOUT_SECONDS = 120;
    private static final Gson gson = new Gson();

    // Cache: agentUrl -> resolved client + card
    private static final Map<String, AgentConnection> connections = new ConcurrentHashMap<>();

    private record AgentConnection(Client client, AgentCard card, AtomicReference<Boolean> discoveryEmitted) {
        AgentConnection(Client client, AgentCard card) {
            this(client, card, new AtomicReference<>(false));
        }
    }

    /**
     * Execute a message to any remote A2A agent, given its URL.
     */
    public static String execute(String toolName, Map<String, Object> toolUseInput) {
        Map<String, Object> params = unwrapParams(toolUseInput);

        if (params == null || !params.containsKey("agentUrl") || !params.containsKey("message")) {
            throw new IllegalArgumentException("Invalid input: 'agentUrl' and 'message' are required.");
        }

        String agentUrl = params.get("agentUrl").toString();
        String messageText = params.get("message").toString();
        String taskId = params.containsKey("taskId") ? params.get("taskId").toString() : null;
        String contextId = params.containsKey("contextId") ? params.get("contextId").toString() : null;
        boolean isResume = taskId != null && contextId != null;

        System.out.println("[A2ATool] " + (isResume ? "Resuming" : "Contacting") + " agent at " + agentUrl + ": " + messageText);

        try {
            AgentConnection conn = getOrCreateConnection(agentUrl);

            // Build message
            Message message;
            if (isResume) {
                message = A2A.createUserTextMessage(messageText, contextId, taskId);
                EventClient.emitEvent("a2a_task_resumed", messageText,
                        EventClient.LANE_CLIENT, EventClient.LANE_REMOTE,
                        Map.of("taskId", taskId, "agentName", conn.card().name()));
            } else {
                message = A2A.toUserMessage(messageText);
                EventClient.emitEvent("a2a_task_submitted", messageText,
                        EventClient.LANE_CLIENT, EventClient.LANE_REMOTE,
                        Map.of("message", messageText, "agentName", conn.card().name()));
            }

            return sendAndCollect(conn, message);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return gson.toJson(Map.of("status", "failed", "message", "Request interrupted"));
        } catch (Exception e) {
            System.err.println("[A2ATool] Error contacting " + agentUrl + ": " + e.getMessage());
            e.printStackTrace();
            return gson.toJson(Map.of("status", "failed", "message", "Error: " + e.getMessage()));
        }
    }

    // ── Client lifecycle ─────────────────────────────────────────────────

    private static AgentConnection getOrCreateConnection(String agentUrl) throws Exception {
        AgentConnection existing = connections.get(agentUrl);
        if (existing != null) return existing;

        System.out.println("[A2ATool] Resolving agent card at " + agentUrl);
        AgentCard card = new A2ACardResolver(agentUrl).getAgentCard();
        System.out.println("[A2ATool] Discovered agent: " + card.name());

        ClientConfig clientConfig = new ClientConfig.Builder()
                .setAcceptedOutputModes(List.of("text", "data"))
                .build();
        Client client = Client.builder(card)
                .clientConfig(clientConfig)
                .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
                .build();

        AgentConnection conn = new AgentConnection(client, card);
        connections.put(agentUrl, conn);
        return conn;
    }

    // ── Streaming send / collect ─────────────────────────────────────────

    private static String sendAndCollect(AgentConnection conn, Message message) throws InterruptedException {
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
        } catch (Exception ignored) { }
        // Base metadata for remote-lane events (working, completed, failed, artifact)
        final Map<String, Object> wfMeta;
        {
            Map<String, Object> meta = new HashMap<>();
            meta.put("agentName", agentName);
            meta.put("lane", EventClient.LANE_REMOTE);
            if (capturedWorkflowId != null) meta.put("workflowId", capturedWorkflowId);
            wfMeta = meta;
        }
        // Metadata for events that go remote → client (input_required, completed with result)
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
                            String statusMsg = extractTextFromMessage(tsue.getStatus().message());
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
                                EventClient.emitEvent("a2a_completed", cleanCompletedSummary(statusMsg), new HashMap<>(remoteToClientMeta));

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
                                        if (dataPart.getData() != null) {
                                            eventData.putAll(dataPart.getData());
                                        }
                                        eventData.putAll(remoteToClientMeta);
                                        EventClient.emitEvent("a2a_artifact", artifact.name(), eventData);
                                    } else if (part instanceof TextPart textPart) {
                                        artifactMap.put("text", textPart.getText());
                                        Map<String, Object> textArtData = new HashMap<>(Map.of("title", artifact.name()));
                                        textArtData.putAll(remoteToClientMeta);
                                        EventClient.emitEvent("a2a_artifact", textPart.getText(), textArtData);
                                    }
                                }
                            }

                            collectedArtifacts.add(artifactMap);
                        }

                    } else if (event instanceof MessageEvent messageEvent) {
                        Message msg = messageEvent.getMessage();
                        String text = extractTextFromParts(msg.getParts());
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
                        String stateStr = task.getStatus() != null ? task.getStatus().state().asString() : "completed";

                        if (resultJsonRef.get() == null) {
                            String text = "";
                            if (task.getStatus() != null && task.getStatus().message() != null) {
                                text = extractTextFromMessage(task.getStatus().message());
                            }
                            Map<String, Object> result = new HashMap<>();
                            result.put("status", stateStr);
                            result.put("message", text);

                            if ("input-required".equals(stateStr)) {
                                result.put("taskId", task.getId());
                                result.put("contextId", task.getContextId());
                                Map<String, Object> irEvtData = new HashMap<>(Map.of("taskId", task.getId(), "contextId", task.getContextId()));
                                irEvtData.putAll(remoteToClientMeta);
                                EventClient.emitEvent("a2a_input_required", text, irEvtData);
                            } else if ("working".equals(stateStr)) {
                                EventClient.emitEvent("a2a_working", text, new HashMap<>(wfMeta));
                            } else if ("completed".equals(stateStr)) {
                                EventClient.emitEvent("a2a_completed", cleanCompletedSummary(text), new HashMap<>(remoteToClientMeta));
                            } else if ("failed".equals(stateStr)) {
                                EventClient.emitEvent("a2a_failed", text, new HashMap<>(remoteToClientMeta));
                            }

                            if (!collectedArtifacts.isEmpty()) {
                                result.put("artifacts", collectedArtifacts);
                            }
                            resultJsonRef.set(gson.toJson(result));
                        }
                        // Only count down latch for terminal states — not for 'working'
                        if (!"working".equals(stateStr)) {
                            latch.countDown();
                        }
                    }

                } catch (Exception e) {
                    System.err.println("[A2ATool:" + agentName + "] Error processing event: " + e.getMessage());
                    e.printStackTrace();
                    errorRef.set(e.getMessage());
                    latch.countDown();
                }
            }
        );

        Consumer<Throwable> errorHandler = error -> {
            if (error == null) {
                // null signals normal SSE stream completion — do NOT count down the
                // latch here.  The event callbacks already count down for every
                // terminal state (COMPLETED, FAILED, INPUT_REQUIRED).  Counting down
                // here races with the callbacks: if the stream closes before the
                // callback sets resultJsonRef, the main thread wakes up to a null
                // result and incorrectly reports "failed".
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
        // states (COMPLETED, FAILED, INPUT_REQUIRED).  A transport-level error
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

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Extract a short one-line summary suitable for the a2a_completed status badge.
     */
    private static String cleanCompletedSummary(String text) {
        if (text == null || text.isEmpty()) return "Task completed";
        String cleaned = text.replaceAll("(?si)<thinking>.*?</thinking>", "").trim();
        cleaned = cleaned.replaceAll("(?i)^.*?(?:let'?s respond\\.?\\s*|here(?:'s| is) (?:my |the )?(?:final )?response[.:]?\\s*)", "").trim();
        String[] sentences = cleaned.split("(?<=[.!?])\\s+", 2);
        String first = sentences[0].trim();
        if (first.length() > 120) {
            first = first.substring(0, 117) + "...";
        }
        return first.isEmpty() ? "Task completed" : first;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> unwrapParams(Map<String, Object> toolUseInput) {
        if (toolUseInput.containsKey("map") && toolUseInput.get("map") instanceof Map) {
            return (Map<String, Object>) toolUseInput.get("map");
        }
        return toolUseInput;
    }

    private static String extractTextFromMessage(Message msg) {
        if (msg == null || msg.getParts() == null) return "";
        return extractTextFromParts(msg.getParts());
    }

    private static String extractTextFromParts(List<Part<?>> parts) {
        if (parts == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Part<?> part : parts) {
            if (part instanceof TextPart textPart) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(textPart.getText());
            }
        }
        return sb.toString();
    }

    // ── Bedrock tool definition ──────────────────────────────────────────

    public static Tool getBedrockTool() {
        Map<String, Document> agentUrlProp = new HashMap<>();
        agentUrlProp.put("type", Document.fromString("string"));
        agentUrlProp.put("description", Document.fromString(
                "The base URL of the remote A2A agent (from search_agent_registry results)"));

        Map<String, Document> messageProp = new HashMap<>();
        messageProp.put("type", Document.fromString("string"));
        messageProp.put("description", Document.fromString("The message to send to the agent"));

        Map<String, Document> taskIdProp = new HashMap<>();
        taskIdProp.put("type", Document.fromString("string"));
        taskIdProp.put("description", Document.fromString(
                "The task ID from a previous input_required response (for multi-turn conversations)"));

        Map<String, Document> contextIdProp = new HashMap<>();
        contextIdProp.put("type", Document.fromString("string"));
        contextIdProp.put("description", Document.fromString(
                "The context ID from a previous input_required response (for multi-turn conversations)"));

        Map<String, Document> properties = new HashMap<>();
        properties.put("agentUrl", Document.fromMap(agentUrlProp));
        properties.put("message", Document.fromMap(messageProp));
        properties.put("taskId", Document.fromMap(taskIdProp));
        properties.put("contextId", Document.fromMap(contextIdProp));

        Map<String, Document> root = new HashMap<>();
        root.put("type", Document.fromString("object"));
        root.put("properties", Document.fromMap(properties));
        root.put("required", Document.fromList(List.of(
                Document.fromString("agentUrl"),
                Document.fromString("message"))));

        return Tool.builder()
                .toolSpec(ToolSpecification.builder()
                        .name("a2a_send_message")
                        .description("Send a message to a remote A2A agent. "
                                + "Use the agentUrl returned by search_agent_registry. "
                                + "Supports multi-turn conversations via taskId/contextId.")
                        .inputSchema(ToolInputSchema.builder()
                                .json(Document.fromMap(root))
                                .build())
                        .build())
                .build();
    }
}
