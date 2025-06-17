package bitovi.workflows.AgentGoal.helpers.agents;

import java.util.ArrayList;
import java.util.List;

import bitovi.common.Agent;
import bitovi.workflows.AgentGoal.helpers.AgentSelectionHelper;
import bitovi.workflows.AgentGoal.helpers.AgentToolArgument;
import bitovi.workflows.AgentGoal.helpers.AgentToolArgumentType;
import bitovi.workflows.AgentGoal.helpers.AgentToolDefinition;

public class ChooseGoalAgent {
    private static Agent _agent = null;

    public static Agent build() {
        if (_agent == null) {
            Agent agent = new Agent("goal_choose_agent_type");
            agent.setCategoryTag("agent_selection");
            agent.setName("Choose Agent");
            agent.setDescription(
                    "Choose the type of agent to assist you today. You can always interrupt an existing agent to pick a new one.");
            agent.setStarterPrompt(
                    "Welcome me, give me a description of what you can do, then ask me for the details you need to do your job. List all details of all agents as provided by the output of the first tool included in this goal.");
            agent.setExampleConversation("agent: Here are the currently available agents." +
                    "tool_result: { agents: 'agent_name': 'Event Flight Finder', 'goal_id': 'goal_event_flight_invoice', 'agent_description': 'Helps users find interesting events and arrange travel to them',"
                    +
                    "'agent_name': 'Schedule PTO', 'goal_id': 'goal_hr_schedule_pto', 'agent_description': 'Schedule PTO based on your available PTO.' }"
                    +
                    "agent: The available agents are: Event Flight Finder and Schedule PTO. \n Which agent would you like to work with? "
                    +
                    "user: I'd like to find an event and book flights using the Event Flight Finder" +
                    "user_confirmed_tool_run: <user clicks confirm on ChangeGoal tool>" +
                    "tool_result: { 'new_goal': 'goal_event_flight_invoice' }");

            // Define the tools that this agent can use
            agent.registerTool(getListAgentsTool());
            agent.registerTool(getChangeGoalTool());

            _agent = agent;
        }

        return _agent;
    }

    private static AgentToolDefinition getListAgentsTool() {
        return new AgentToolDefinition("ListAgents",
                "List available agents to interact with, pulled from goal_registry.", null);
    }

    public static String executeListAgentsTool() {
        List<String> goalCategories = List.of("all");

        // if multi-goal-mode, add agent_selection as a goal (defaults to True)
        if (!goalCategories.contains("agent_selection")) {
            String firstGoalValue = System.getenv("AGENT_GOAL");
            if (firstGoalValue == null ||
                    firstGoalValue.toLowerCase().equals("goal_choose_agent_type")) {
                goalCategories.add("agent_selection");
            }
        }

        // always show goals labeled as "system," like the goal chooser
        if (!goalCategories.contains("system")) {
            goalCategories.add("system");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Here are the available agents:\n");

        if (AgentSelectionHelper.getGoalList() != null) {
            for (Agent goal : AgentSelectionHelper.getGoalList()) {
                if (goalCategories.contains("all") ||
                        goalCategories.contains(goal.categoryTag)) {
                    sb.append(String.format("Agent Name: %s\n", goal.agentName));
                    sb.append(String.format("Description: %s\n", goal.agentDescription));
                    sb.append(String.format("Goal ID: %s\n", goal.id));

                    sb.append("\n\n");
                }
            }
        }

        return sb.toString();
    }

    private static AgentToolDefinition getChangeGoalTool() {

        ArrayList<AgentToolArgument> arguments = new ArrayList<>();
        arguments.add(new AgentToolArgument("goal_id", "The ID of the goal to change to.",
                AgentToolArgumentType.STRING, true));

        return new AgentToolDefinition("ChangeGoal",
                "Change the goal of the active agent.",
                arguments);
    }
}
