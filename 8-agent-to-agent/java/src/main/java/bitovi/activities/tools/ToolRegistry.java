package bitovi.activities.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import software.amazon.awssdk.services.bedrockruntime.model.Tool;

/**
 * Registry for all available tools in the agent workflow.
 *
 * This exercise focuses on A2A (Agent-to-Agent) communication, so only two
 * tools are registered:
 * 1. search_agent_registry — discover remote A2A agents by keyword
 * 2. a2a_send_message — send a message to a discovered agent
 *
 * The LLM sees these tool definitions (via getToolsAsXmlString) in the thought
 * prompt and decides when to call them. The workflow's actionActivity routes
 * the call here for execution.
 */
public class ToolRegistry {
    private static final Map<String, BiFunction<String, Map<String, Object>, String>> toolExecutors = new HashMap<>();

    static {
        // A2A agent discovery and communication tools
        toolExecutors.put("search_agent_registry", AgentRegistryTool::execute);
        toolExecutors.put("a2a_send_message", A2ATool::execute);
    }

    /**
     * Get all Bedrock tool definitions for the agent.
     * These are serialized to XML and injected into the thought prompt so the LLM
     * knows what tools it can invoke.
     *
     * @return List of AWS Bedrock Tool schema objects
     */
    public static List<Tool> getAllBedrockTools() {
        List<Tool> tools = new ArrayList<>();
        tools.add(AgentRegistryTool.getBedrockTool());
        tools.add(A2ATool.getBedrockTool());
        return tools;
    }

    /**
     * Get all tools formatted as XML string for agent consumption.
     * This format is used in the thought prompt to tell the AI what tools are
     * available.
     * 
     * @return XML string describing all available tools
     */
    public static String getToolsAsXmlString() {
        StringBuilder xml = new StringBuilder();
        List<Tool> tools = getAllBedrockTools();

        for (Tool tool : tools) {
            String name = tool.toolSpec().name();
            String description = tool.toolSpec().description();
            String schema = tool.toolSpec().inputSchema().json().toString();

            xml.append("<tool>\n");
            xml.append("    <name>").append(name).append("</name>\n");
            xml.append("    <description>").append(description).append("</description>\n");
            xml.append("    <schema>").append(schema).append("</schema>\n");
            xml.append("</tool>\n");
        }

        return xml.toString();
    }

    /**
     * Execute a tool by name with the provided input.
     * 
     * @param toolName The name of the tool to execute
     * @param input    The input parameters for the tool
     * @return The result of the tool execution as a string
     * @throws IllegalArgumentException if tool not found
     */
    public static String executeTool(String toolName, Map<String, Object> input) {
        BiFunction<String, Map<String, Object>, String> executor = toolExecutors.get(toolName);

        if (executor == null) {
            throw new IllegalArgumentException("Tool not found: " + toolName);
        }

        return executor.apply(toolName, input);
    }

    /**
     * Check if a tool is registered.
     * 
     * @param toolName The name of the tool to check
     * @return true if the tool exists, false otherwise
     */
    public static boolean hasToolNamed(String toolName) {
        return toolExecutors.containsKey(toolName);
    }
}
