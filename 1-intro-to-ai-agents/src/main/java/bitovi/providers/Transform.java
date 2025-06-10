package bitovi.providers;

import java.util.ArrayList;

import bitovi.records.MessageRecord;

public class Transform {
    public static String LLMProviderChatMessagesToString(ArrayList<MessageRecord> messages) {
        StringBuilder sb = new StringBuilder();
        for (MessageRecord message : messages) {
            String str = LLMProviderChatMessageToString(message);
            sb.append(str);
            sb.append("\n");
        }
        return sb.toString();
    }

    public static String LLMProviderChatMessageToString(MessageRecord message) {
        return message.role() + ": " + message.content();
    }
}
