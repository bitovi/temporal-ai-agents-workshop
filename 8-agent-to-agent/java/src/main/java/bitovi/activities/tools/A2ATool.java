package bitovi.activities.tools;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bitovi.activities.a2a.A2AHandler;
import bitovi.activities.a2a.A2AHelpers;
import bitovi.activities.a2a.A2ARegistry;
import bitovi.activities.a2a.A2ARegistry.AgentConnection;
import bitovi.activities.a2a.types.A2ARequestInput;
import bitovi.common.EventClient;
import io.a2a.A2A;
import io.a2a.spec.Message;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;

public class A2ATool {
        public static String execute(String toolName, Map<String, Object> toolUseInput) {
                A2ARequestInput params = A2AHelpers.validateToolParams(toolUseInput);

                try {
                        System.out.println("[A2ATool] " + (params.existingTask() ? "Resuming" : "Contacting")
                                        + " agent at "
                                        + params.agentUrl() + ": "
                                        + params.message());

                        Message message = params.existingTask()
                                        ? A2A.createUserTextMessage(params.message(), params.contextId(),
                                                        params.taskId())
                                        : A2A.createUserTextMessage(params.message(), null, null);
                        AgentConnection conn = A2ARegistry.getOrCreateConnection(params.agentUrl());

                        if (params.existingTask()) {
                                EventClient.emitEvent("a2a_task_resumed", params.message(),
                                                EventClient.LANE_CLIENT, EventClient.LANE_REMOTE,
                                                Map.of("taskId", params.taskId(), "agentName",
                                                                conn.card().name()));
                        } else {
                                EventClient.emitEvent("a2a_task_submitted", params.message(),
                                                EventClient.LANE_CLIENT, EventClient.LANE_REMOTE,
                                                Map.of("message", params.message(), "agentName",
                                                                conn.card().name()));
                        }
                        return A2AHandler.sendAndCollect(conn, message);

                } catch (Exception e) {
                        System.err.println(
                                        "[A2ATool] Error contacting agent at " + params.agentUrl() + ": "
                                                        + e.getMessage());
                        return "[Error] Failed to contact agent at " + params.agentUrl() + ": "
                                        + e.getMessage();
                }
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
