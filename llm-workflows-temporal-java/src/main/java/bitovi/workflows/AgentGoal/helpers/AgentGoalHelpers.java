package bitovi.workflows.AgentGoal.helpers;

import java.util.ArrayList;

import org.json.JSONObject;

import bitovi.common.Agent;
import bitovi.common.DataTypes;
import bitovi.workflows.AgentGoal.AgentGoalTypes.AgentGoalConversationEntry;
import bitovi.workflows.AgentGoal.AgentGoalTypes.AgentGoalConversationHistory;

public class AgentGoalHelpers {
    public static void handleToolExecution(String currentTool, AgentToolPlannerResult toolData,
            ArrayList<JSONObject> toolResults,
            ArrayList<String> promptQueue, Agent goal) {
        throw new UnsupportedOperationException("Tool execution handling is not implemented yet.");
    }

    public static DataTypes.PromptSummaryRecord promptSummaryWithHistory(
            AgentGoalConversationHistory conversationHistory) {
        String historyString = formatHistory(conversationHistory);

        String contextInstructions = "Here is the conversation history between a user and a chatbot: \n"
                + historyString;

        StringBuilder actualPrompt = new StringBuilder();

        actualPrompt.append("Please produce a two sentence summary of this conversation.");
        actualPrompt.append("Put the summary in the format { \"summary\": \"<plain text>\" }\"");

        return new DataTypes.PromptSummaryRecord(contextInstructions, actualPrompt.toString());
    }

    public static String formatHistory(AgentGoalConversationHistory conversationHistory) {
        StringBuilder historyBuilder = new StringBuilder();
        for (AgentGoalConversationEntry message : conversationHistory.messages()) {
            historyBuilder.append(message.actor())
                    .append(": ")
                    .append(message.response())
                    .append("\n");
        }
        return historyBuilder.toString();
    }
}
