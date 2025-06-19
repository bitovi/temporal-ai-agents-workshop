package bitovi.workflows.AgentGoal.helpers;

import org.json.JSONObject;

public class AgentToolPlannerResult {

    public boolean forceConfirm;
    public String tool;
    public String nextStep;
    public JSONObject args;

    public static AgentToolPlannerResult from(JSONObject structuredOutput) {
        AgentToolPlannerResult result = new AgentToolPlannerResult();
        result.forceConfirm = Boolean.parseBoolean(structuredOutput.optString("forceConfirm", "false"));
        result.tool = structuredOutput.optString("tool", "");
        result.nextStep = structuredOutput.optString("nextStep", "");
        result.args = structuredOutput.optJSONObject("args");
        if (result.args == null) {
            result.args = new JSONObject(); // Default to an empty object if args is not provided
        }

        return result;
    }

    public static AgentToolPlannerResult copy(AgentToolPlannerResult other) {
        AgentToolPlannerResult result = new AgentToolPlannerResult();
        result.forceConfirm = other.forceConfirm;
        result.tool = other.tool;
        result.nextStep = other.nextStep;
        result.args = other.args;
        return result;
    }

    private AgentToolPlannerResult() {
        // Default constructor for deserialization or other internal use
    }
}
