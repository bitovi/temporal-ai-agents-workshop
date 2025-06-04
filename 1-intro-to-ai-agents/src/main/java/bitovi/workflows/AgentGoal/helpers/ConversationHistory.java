package bitovi.workflows.AgentGoal.helpers;

import java.util.ArrayList;

public class ConversationHistory {
    private ArrayList<ChatMessage> messages;

    public ConversationHistory() {
        this.messages = new ArrayList<>();
    }

    public void addMessage(ChatMessage message) {
        this.messages.add(message);
    }

    public ArrayList<ChatMessage> getConversation() {
        return this.messages;
    }

    public void clear() {
        this.messages.clear();
    }
}
