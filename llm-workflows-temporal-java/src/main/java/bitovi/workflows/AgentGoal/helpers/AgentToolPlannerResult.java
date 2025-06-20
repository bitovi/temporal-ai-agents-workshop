package bitovi.workflows.AgentGoal.helpers;

import org.json.JSONObject;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AgentToolPlannerResult {

    public String response;
    public boolean forceConfirm;
    public String tool;
    public String nextStep;
    public JSONObject args;

    public static AgentToolPlannerResult from(JSONObject structuredOutput) {
        AgentToolPlannerResult result = new AgentToolPlannerResult();
        result.response = structuredOutput.optString("response", "");
        result.forceConfirm = Boolean.parseBoolean(structuredOutput.optString("forceConfirm", "false"));
        result.nextStep = structuredOutput.optString("nextStep", "");

        String toolName = structuredOutput.optString("toolName");
        if (toolName == null || toolName.isEmpty()) {
            result.tool = "";
            result.args = null;
        } else {
            result.tool = toolName;
            JSONObject argsObject = structuredOutput.optJSONObject("toolArgs");
            if (argsObject != null) {
                result.args = argsObject;
            } else {
                result.args = null;
            }
        }

        return result;
    }

    public static AgentToolPlannerResult copy(AgentToolPlannerResult other) {
        AgentToolPlannerResult result = new AgentToolPlannerResult();
        result.forceConfirm = other.forceConfirm;
        result.tool = other.tool;
        result.response = other.response;
        result.nextStep = other.nextStep;
        result.args = other.args;
        return result;
    }

    private AgentToolPlannerResult() {
        // Private constructor for internal use, to ensure all fields are set through
        // the static methods
    }

    public AgentToolPlannerResult(
            @JsonProperty("response") String response,
            @JsonProperty("forceConfirm") boolean forceConfirm,
            @JsonProperty("tool") String tool,
            @JsonProperty("nextStep") String nextStep,
            @JsonProperty("args") JSONObject args) {
        // Default constructor for deserialization or other internal use
        this.response = response;
        this.forceConfirm = forceConfirm;
        this.tool = tool;
        this.nextStep = nextStep;
        this.args = args;
    }

    // implement toString method for better logging
    @Override
    public String toString() {
        return "" +
                "response='" + response + '\'' +
                ", forceConfirm=" + forceConfirm +
                ", tool='" + tool + '\'' +
                ", nextStep='" + nextStep + '\'' +
                ", args=" + (args != null ? args.toString() : "null") +
                "";
    }
}
