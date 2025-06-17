package bitovi.workflows.AgentGoal.helpers;

import java.util.ArrayList;

import bitovi.common.Agent;
import bitovi.workflows.AgentGoal.helpers.agents.ChooseGoalAgent;

public class AgentSelectionHelper {
    public static ArrayList<Agent> getGoalList() {
        ArrayList<Agent> goals = new ArrayList<>();
        goals.add(ChooseGoalAgent.build());
        return goals;
    }
}
