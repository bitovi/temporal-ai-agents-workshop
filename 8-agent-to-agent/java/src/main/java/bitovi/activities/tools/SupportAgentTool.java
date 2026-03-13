package bitovi.activities.tools;

import java.io.IOException;
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
import io.a2a.spec.AgentSkill;
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

import bitovi.common.Config;
import bitovi.common.EventClient;

public class SupportAgentTool {
    private static final String SUPPORT_AGENT_URL = new Config().getProperty("SUPPORT_AGENT_SERVER_BASE_URL");
    private static final int TIMEOUT_SECONDS = 120;
    private static final Gson gson = new Gson();

    // Static singleton client - thread-safe and reused across all calls
    private static volatile Client client;
    private static volatile AgentCard agentCard;
    private static final Object lock = new Object();
    private static volatile boolean discoveryEmitted = false;

    /**
     * Get or initialize the A2A client using lazy initialization.
     */
    private static Client getClient() throws IOException {
        if (client == null) {
            synchronized (lock) {
                if (client == null) {
                    try {
                        System.out.println("[SupportAgentTool] Initializing A2A client at " + SUPPORT_AGENT_URL);

                        agentCard = new A2ACardResolver(SUPPORT_AGENT_URL).getAgentCard();
                        System.out.println("[SupportAgentTool] Fetched agent card: " + agentCard.name());

                        ClientConfig clientConfig = new ClientConfig.Builder()
                                .setAcceptedOutputModes(List.of("text", "data"))
                                .build();

                        client = Client.builder(agentCard)
                                .clientConfig(clientConfig)
                                .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
                                .build();

                        System.out.println("[SupportAgentTool] A2A client initialized successfully");
                    } catch (Exception e) {
                        System.err.println("[SupportAgentTool] Failed to initialize A2A client: " + e.getMessage());
                        throw new IOException("Failed to initialize Support Agent client: " + e.getMessage(), e);
                    }
                }
            }
        }
        return client;
    }

    /**
     * Build the tool description from the agent card.
     */
    private static String buildToolDescription(AgentCard card) {
        if (card == null) {
            return "Contact the Riot Games support agent for billing inquiries, refunds, and account issues";
        }

        StringBuilder description = new StringBuilder(card.description());

        if (card.skills() != null && !card.skills().isEmpty()) {
            description.append(" Skills: ");
            for (int i = 0; i < card.skills().size(); i++) {
                AgentSkill skill = card.skills().get(i);
                if (i > 0) description.append(", ");
                description.append(skill.name());
            }
        }

        return description.toString();
    }

    /**
     * Emit a2a_discovery event with agent card info (once per workflow).
     */
    private static void emitDiscovery() {
        if (!discoveryEmitted && agentCard != null) {
            discoveryEmitted = true;
            Map<String, Object> data = new HashMap<>();
            data.put("name", agentCard.name());
            data.put("description", agentCard.description());
            if (agentCard.skills() != null) {
                List<Map<String, String>> skillsList = new ArrayList<>();
                for (AgentSkill skill : agentCard.skills()) {
                    Map<String, String> s = new HashMap<>();
                    s.put("id", skill.id());
                    s.put("name", skill.name());
                    s.put("description", skill.description());
                    skillsList.add(s);
                }
                data.put("skills", skillsList);
            }
            EventClient.emitEvent("a2a_discovery", agentCard.name() + " — " + agentCard.description(), data);
        }
    }

    /**
     * Execute a query to the Support Agent via A2A protocol.
     * Supports multi-turn: pass taskId and contextId to resume an existing task.
     */
    public static String execute(String toolName, Map<String, Object> toolUseInput) {
        System.out.println("[SupportAgentTool] Executing with inputs: " + toolUseInput);

        // Extract the actual parameter map if it's nested under "map" key
        Map<String, Object> params = toolUseInput;
        if (toolUseInput.containsKey("map") && toolUseInput.get("map") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> nested = (Map<String, Object>) toolUseInput.get("map");
            params = nested;
        }

        if (params == null || !params.containsKey("message")) {
            throw new IllegalArgumentException("Invalid input: 'message' is required.");
        }

        String messageText = params.get("message").toString();
        String taskId = params.containsKey("taskId") ? params.get("taskId").toString() : null;
        String contextId = params.containsKey("contextId") ? params.get("contextId").toString() : null;

        boolean isResume = taskId != null && contextId != null;
        System.out.println("[SupportAgentTool] " + (isResume ? "Resuming" : "Starting") + " support request: " + messageText);

        try {
            Client supportClient = getClient();
            emitDiscovery();

            // Build message
            Message message;
            if (isResume) {
                message = A2A.createUserTextMessage(messageText, contextId, taskId);
                EventClient.emitEvent("a2a_task_resumed", "Resumed task with " + agentCard.name(),
                        Map.of("taskId", taskId));
            } else {
                message = A2A.toUserMessage(messageText);
                EventClient.emitEvent("a2a_task_submitted", "Opened task with " + agentCard.name(),
                        Map.of("message", messageText));
            }

            // Accumulate response
            StringBuilder responseBuilder = new StringBuilder();
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> errorRef = new AtomicReference<>();
            AtomicReference<String> resultJsonRef = new AtomicReference<>();
            List<Map<String, Object>> collectedArtifacts = new ArrayList<>();

            List<BiConsumer<ClientEvent, AgentCard>> consumers = List.of(
                (event, card) -> {
                    try {
                        if (event instanceof TaskUpdateEvent tue) {
                            UpdateEvent ue = tue.getUpdateEvent();

                            if (ue instanceof TaskStatusUpdateEvent tsue) {
                                TaskState state = tsue.getStatus().state();
                                String statusMsg = extractTextFromMessage(tsue.getStatus().message());
                                System.out.println("[SupportAgentTool] Status: " + state + " — " + statusMsg);

                                if (state == TaskState.WORKING) {
                                    EventClient.emitEvent("a2a_working", statusMsg);

                                } else if (state == TaskState.INPUT_REQUIRED) {
                                    String returnTaskId = tsue.getTaskId();
                                    String returnContextId = tsue.getContextId();

                                    Map<String, Object> irData = new HashMap<>();
                                    irData.put("taskId", returnTaskId);
                                    irData.put("contextId", returnContextId);
                                    EventClient.emitEvent("a2a_input_required", statusMsg, irData);

                                    // Build structured return JSON
                                    Map<String, Object> result = new HashMap<>();
                                    result.put("status", "input_required");
                                    result.put("taskId", returnTaskId);
                                    result.put("contextId", returnContextId);
                                    result.put("message", statusMsg);
                                    resultJsonRef.set(gson.toJson(result));
                                    latch.countDown();

                                } else if (state == TaskState.COMPLETED) {
                                    EventClient.emitEvent("a2a_completed", statusMsg);

                                    Map<String, Object> result = new HashMap<>();
                                    result.put("status", "completed");
                                    result.put("message", statusMsg);
                                    if (!collectedArtifacts.isEmpty()) {
                                        result.put("artifacts", collectedArtifacts);
                                    }
                                    resultJsonRef.set(gson.toJson(result));
                                    latch.countDown();

                                } else if (state == TaskState.FAILED) {
                                    EventClient.emitEvent("a2a_completed", "Task failed: " + statusMsg);

                                    Map<String, Object> result = new HashMap<>();
                                    result.put("status", "failed");
                                    result.put("message", statusMsg);
                                    resultJsonRef.set(gson.toJson(result));
                                    latch.countDown();
                                }

                            } else if (ue instanceof TaskArtifactUpdateEvent taue) {
                                Artifact artifact = taue.getArtifact();
                                System.out.println("[SupportAgentTool] Artifact: " + artifact.name());

                                Map<String, Object> artifactMap = new HashMap<>();
                                artifactMap.put("title", artifact.name());

                                // Extract data from DataPart
                                if (artifact.parts() != null) {
                                    for (Part<?> part : artifact.parts()) {
                                        if (part instanceof DataPart dataPart) {
                                            artifactMap.put("data", dataPart.getData());
                                            // Emit artifact event with data at top level
                                            Map<String, Object> eventData = new HashMap<>();
                                            eventData.put("title", artifact.name());
                                            if (dataPart.getData() != null) {
                                                eventData.putAll(dataPart.getData());
                                            }
                                            EventClient.emitEvent("a2a_artifact", artifact.name(), eventData);
                                        } else if (part instanceof TextPart textPart) {
                                            artifactMap.put("text", textPart.getText());
                                            EventClient.emitEvent("a2a_artifact", textPart.getText(),
                                                    Map.of("title", artifact.name()));
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
                                System.out.println("[SupportAgentTool] Message: " + text);

                                // If we haven't built a result yet, build one from the message
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
                            System.out.println("[SupportAgentTool] TaskEvent received");
                            if (resultJsonRef.get() == null) {
                                // Build result from task
                                io.a2a.spec.Task task = taskEvent.getTask();
                                String text = "";
                                if (task.getStatus() != null && task.getStatus().message() != null) {
                                    text = extractTextFromMessage(task.getStatus().message());
                                }
                                String stateStr = task.getStatus() != null ? task.getStatus().state().asString() : "completed";
                                Map<String, Object> result = new HashMap<>();
                                result.put("status", stateStr);
                                result.put("message", text);

                                // For input_required, include taskId/contextId for multi-turn
                                if ("input-required".equals(stateStr)) {
                                    result.put("taskId", task.getId());
                                    result.put("contextId", task.getContextId());
                                    EventClient.emitEvent("a2a_input_required", text,
                                            Map.of("taskId", task.getId(), "contextId", task.getContextId()));
                                } else if ("completed".equals(stateStr)) {
                                    EventClient.emitEvent("a2a_completed", text);
                                } else if ("failed".equals(stateStr)) {
                                    EventClient.emitEvent("a2a_completed", "Task failed: " + text);
                                }

                                if (!collectedArtifacts.isEmpty()) {
                                    result.put("artifacts", collectedArtifacts);
                                }
                                resultJsonRef.set(gson.toJson(result));
                            }
                            latch.countDown();
                        }

                    } catch (Exception e) {
                        System.err.println("[SupportAgentTool] Error processing event: " + e.getMessage());
                        e.printStackTrace();
                        errorRef.set(e.getMessage());
                        latch.countDown();
                    }
                }
            );

            Consumer<Throwable> errorHandler = error -> {
                System.err.println("[SupportAgentTool] Error: " + error.getMessage());
                errorRef.set(error.getMessage());
                latch.countDown();
            };

            System.out.println("[SupportAgentTool] Sending message...");
            supportClient.sendMessage(message, consumers, errorHandler);

            boolean completed = latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!completed) {
                String timeoutMsg = "Request timed out after " + TIMEOUT_SECONDS + " seconds.";
                System.err.println("[SupportAgentTool] " + timeoutMsg);
                return gson.toJson(Map.of("status", "failed", "message", timeoutMsg));
            }

            String error = errorRef.get();
            if (error != null) {
                return gson.toJson(Map.of("status", "failed", "message", "Error: " + error));
            }

            String resultJson = resultJsonRef.get();
            if (resultJson != null) {
                System.out.println("[SupportAgentTool] Result: " + resultJson);
                return resultJson;
            }

            // Fallback
            String text = responseBuilder.toString();
            if (!text.isEmpty()) {
                return gson.toJson(Map.of("status", "completed", "message", text));
            }

            return gson.toJson(Map.of("status", "failed", "message", "No response received from support agent"));

        } catch (IOException e) {
            String errorMsg = "Failed to connect to Support Agent at " + SUPPORT_AGENT_URL + ": " + e.getMessage();
            System.err.println("[SupportAgentTool] " + errorMsg);
            return gson.toJson(Map.of("status", "failed", "message", errorMsg));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return gson.toJson(Map.of("status", "failed", "message", "Request interrupted"));
        } catch (Exception e) {
            System.err.println("[SupportAgentTool] Unexpected error: " + e.getMessage());
            e.printStackTrace();
            return gson.toJson(Map.of("status", "failed", "message", "Unexpected error: " + e.getMessage()));
        }
    }

    /**
     * Extract text from a Message's parts.
     */
    private static String extractTextFromMessage(Message msg) {
        if (msg == null || msg.getParts() == null) return "";
        return extractTextFromParts(msg.getParts());
    }

    /**
     * Extract text content from a list of parts.
     */
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

    /**
     * Get the Bedrock tool definition for the support agent.
     */
    public static Tool getBedrockTool() {
        // 'message' property (string, required)
        Map<String, Document> messagePropertyMap = new HashMap<>();
        messagePropertyMap.put("type", Document.fromString("string"));
        messagePropertyMap.put("description", Document.fromString("The message to send to the support agent"));

        // 'taskId' property (string, optional — for resuming multi-turn)
        Map<String, Document> taskIdPropertyMap = new HashMap<>();
        taskIdPropertyMap.put("type", Document.fromString("string"));
        taskIdPropertyMap.put("description", Document.fromString("The task ID from a previous input_required response (for multi-turn)"));

        // 'contextId' property (string, optional — for resuming multi-turn)
        Map<String, Document> contextIdPropertyMap = new HashMap<>();
        contextIdPropertyMap.put("type", Document.fromString("string"));
        contextIdPropertyMap.put("description", Document.fromString("The context ID from a previous input_required response (for multi-turn)"));

        Map<String, Document> propertiesMap = new HashMap<>();
        propertiesMap.put("message", Document.fromMap(messagePropertyMap));
        propertiesMap.put("taskId", Document.fromMap(taskIdPropertyMap));
        propertiesMap.put("contextId", Document.fromMap(contextIdPropertyMap));

        List<Document> requiredList = new ArrayList<>();
        requiredList.add(Document.fromString("message"));

        Map<String, Document> rootMap = new HashMap<>();
        rootMap.put("type", Document.fromString("object"));
        rootMap.put("properties", Document.fromMap(propertiesMap));
        rootMap.put("required", Document.fromList(requiredList));

        Document document = Document.fromMap(rootMap);

        String description = buildToolDescription(agentCard);

        ToolSpecification specification = ToolSpecification.builder()
                .name("support_agent")
                .description(description)
                .inputSchema(ToolInputSchema.builder()
                        .json(document)
                        .build())
                .build();

        return Tool.builder()
                .toolSpec(specification)
                .build();
    }
}
