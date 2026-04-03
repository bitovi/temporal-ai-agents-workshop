package bitovi.activities.tools;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bitovi.activities.a2a.A2AHandler;
import bitovi.activities.a2a.A2AHelpers;
import bitovi.activities.a2a.types.A2APayload;
import bitovi.activities.a2a.types.A2ARequestInput;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;

public class A2ATool {

        public static String execute(String toolName, Map<String, Object> toolUseInput) {
                A2ARequestInput params = A2AHelpers.validateToolParams(toolUseInput);
                try {
                        A2APayload payload = A2APayload.fromParams(params);
                        return A2AHandler.execute(payload);
                } catch (Exception e) {
                        System.err.println("[A2ATool] Error executing tool " + toolName + ": " + e.getMessage());
                        return "[Error] Failed to execute tool " + toolName + ": " + e.getMessage();
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
