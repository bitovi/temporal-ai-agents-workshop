package bitovi.providers;

import java.util.ArrayList;

public class Transform {
    public static String LLMProviderChatMessagesToString(ArrayList<LLMProviderChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (LLMProviderChatMessage message : messages) {
            String str = LLMProviderChatMessageToString(message);
            sb.append(str);
            sb.append("\n");
        }
        return sb.toString();
    }

    public static String LLMProviderChatMessageToString(LLMProviderChatMessage message) {
        return message.getRole() + ": " + message.getContent();
    }
}
