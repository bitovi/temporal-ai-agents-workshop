package bitovi.activities.plan;

import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import bitovi.activities.tools.ToolRegistry;
import bitovi.activities.types.ActionInput;
import bitovi.activities.types.PlanStep;
import bitovi.activities.types.PlanStepResult;
import bitovi.common.EventClient;
import io.temporal.failure.ApplicationFailure;

public class ExecutePlanStep {
  public static PlanStepResult execute(PlanStep step, List<PlanStepResult> dependsOn) throws ApplicationFailure {
    String toolName = step.tool_name();
    ActionInput input = step.tool_input();

    System.out.println("executePlanStep called with tool: " + toolName);

    EventClient.emitEvent("status", "Executing...");

    // Check if tool exists
    if (!ToolRegistry.hasToolNamed(toolName)) {
      EventClient.emitEvent("error", "Tool with name " + toolName + " not found.");
      JSONObject errorResult = new JSONObject();
      errorResult.put("name", toolName);
      errorResult.put("input", input.parameters());
      errorResult.put("error", "Tool not found");
      return new PlanStepResult(step.id(), step.tool_name(), step.tool_input(), errorResult.toString(), true);
    }

    // Get parameters from ActionInput
    Map<String, Object> inputMap = input.parameters();

    System.out.println("Tool input parameters: " + new JSONObject(inputMap).toString());
    // Loop over the dependsOn results and perform string substitution in the input
    // parameters if there are any references to previous step results
    for (PlanStepResult dependency : dependsOn) {
      String placeholder = "{{result:" + dependency.id() + "}}";
      if (inputMap.values().stream().anyMatch(value -> value.toString().contains(placeholder))) {
        inputMap.replaceAll((key, value) -> value.toString().replace(placeholder, dependency.result()));
      }
    }

    System.out.println("Tool input parameters after substitution: " + new JSONObject(inputMap).toString());

    // Execute tool
    try {
      EventClient.emitEvent("action", "Invoked tool " + toolName +
          " with input " + new JSONObject(inputMap).toString());

      String result = ToolRegistry.executeTool(toolName, inputMap);
      System.out.println("Tool execution successful: " + toolName);
      return new PlanStepResult(step.id(), step.tool_name(), step.tool_input(), result, false);
    } catch (Exception e) {
      String errorMsg = "Error executing tool " + toolName + ": " + e.getMessage();
      System.err.println(errorMsg);
      EventClient.emitEvent("error", errorMsg);
      JSONObject errorResult = new JSONObject();
      errorResult.put("name", toolName);
      errorResult.put("input", inputMap);
      errorResult.put("error", e.getMessage());
      return new PlanStepResult(step.id(), step.tool_name(), step.tool_input(), errorResult.toString(), true);
    }

  }
}
