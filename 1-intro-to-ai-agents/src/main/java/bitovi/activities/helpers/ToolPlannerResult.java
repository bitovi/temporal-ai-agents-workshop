package bitovi.activities.helpers;

import java.util.ArrayList;

public class ToolPlannerResult {

    public boolean forceConfirm;
    public String toolName;
    public String nextStep;
    public ArrayList<ToolArgs> args;

    public ToolPlannerResult(boolean forceConfirm, String nextStep, String toolName, ArrayList<ToolArgs> args) {
        this.forceConfirm = forceConfirm;
        this.nextStep = nextStep;
        this.toolName = toolName;

        this.args = args;
    }
}
