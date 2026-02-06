package bitovi.activities;

import java.util.Map;

import org.json.JSONObject;

import bitovi.activities.tools.ToolRegistry;
import bitovi.activities.types.ActionInput;
import bitovi.common.EventClient;
import io.temporal.failure.ApplicationFailure;

public class Action {
    public static String execute(String toolName, ActionInput input) {
        try {
            System.out.println("actionActivity called with tool: " + toolName);

            // Check if tool exists
            if (!ToolRegistry.hasToolNamed(toolName)) {
                EventClient.emitEvent("error", "Tool with name " + toolName + " not found.");
                JSONObject errorResult = new JSONObject();
                errorResult.put("name", toolName);
                errorResult.put("input", input.parameters());
                errorResult.put("error", "Tool not found");
                return errorResult.toString();
            }

            // Get parameters from ActionInput
            Map<String, Object> inputMap = input.parameters();

            // Execute tool
            try {
                EventClient.emitEvent("action", "Invoked tool " + toolName +
                        " with input " + new JSONObject(inputMap).toString());

                String result = ToolRegistry.executeTool(toolName, inputMap);
                System.out.println("Tool execution successful: " + toolName);
                return result;
            } catch (Exception e) {
                String errorMsg = "Error executing tool " + toolName + ": " + e.getMessage();
                System.err.println(errorMsg);
                EventClient.emitEvent("error", errorMsg);
                JSONObject errorResult = new JSONObject();
                errorResult.put("name", toolName);
                errorResult.put("input", inputMap);
                errorResult.put("error", e.getMessage());
                return errorResult.toString();
            }

        } catch (Exception e) {
            System.err.println("Error in actionActivity: " + e.getMessage());
            throw ApplicationFailure.newFailure("actionActivity failed: " + e.getMessage(),
                    "ActionActivityError");
        }
    }
}
