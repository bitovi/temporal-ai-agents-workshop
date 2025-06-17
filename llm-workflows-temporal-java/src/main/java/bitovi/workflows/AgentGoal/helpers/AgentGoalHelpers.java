package bitovi.workflows.AgentGoal.helpers;

import java.util.ArrayList;
import java.util.List;

import bitovi.common.Agent;
import bitovi.common.DataTypes;
import bitovi.common.DataTypes.MessageRecord;
import bitovi.common.DataTypes.ToolDataRecord;
import bitovi.common.Unknown;

public class AgentGoalHelpers {
    public static List<Unknown> handleToolExecution(String currentTool, ToolDataRecord toolData,
            ArrayList<MessageRecord> promptQueue, Agent goal) {
        throw new UnsupportedOperationException("Tool execution handling is not implemented yet.");
    }

    public static DataTypes.PromptSummaryRecord promptSummaryWithHistory(
            ArrayList<DataTypes.MessageRecord> conversationHistory) {
        String historyString = formatHistory(conversationHistory);

        String contextInstructions = "Here is the conversation history between a user and a chatbot: " + historyString;

        ArrayList<DataTypes.MessageRecord> actualPrompt = new ArrayList<>();

        actualPrompt.add(
                new DataTypes.MessageRecord("system", "Please produce a two sentence summary of this conversation."));
        actualPrompt.add(new DataTypes.MessageRecord("system",
                "Put the summary in the format { \"summary\": \"<plain text>\" }"));

        return new DataTypes.PromptSummaryRecord(contextInstructions, actualPrompt);
    }

    public static String formatHistory(ArrayList<DataTypes.MessageRecord> conversationHistory) {
        StringBuilder historyBuilder = new StringBuilder();
        for (DataTypes.MessageRecord message : conversationHistory) {
            historyBuilder.append(message.role()).append(": ").append(message.content()).append("\n");
        }
        return historyBuilder.toString();
    }
}
