package bitovi.activities.a2a;

import java.util.List;
import java.util.Map;

import bitovi.activities.a2a.types.A2ARequestInput;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.TextPart;

public class A2AHelpers {

    /**
     * Extract a short one-line summary suitable for the a2a_completed status badge.
     */
    public static String cleanCompletedSummary(String text) {
        if (text == null || text.isEmpty())
            return "Task completed";

        String cleaned = text.replaceAll("(?si)<thinking>.*?</thinking>", "").trim();

        String[] sentences = cleaned.split("(?<=[.!?])\\s+", 2);
        String first = sentences[0].trim();
        if (first.length() > 120) {
            first = first.substring(0, 117) + "...";
        }
        return first.isEmpty() ? "Task completed" : first;
    }

    @SuppressWarnings("unchecked")
    public static A2ARequestInput validateToolParams(Map<String, Object> toolUseInput) {
        if (toolUseInput == null) {
            System.err.println("[A2AHelpers] Received null input for tool parameters.");
            throw new IllegalArgumentException("Invalid input: null");
        }

        Map<String, Object> result = toolUseInput;

        if (toolUseInput.containsKey("map") && toolUseInput.get("map") instanceof Map) {
            System.out.println("[A2AHelpers] Extracting parameters from 'map' key.");
            result = (Map<String, Object>) toolUseInput.get("map");
        }

        if (!result.containsKey("agentUrl") || !result.containsKey("message")) {
            System.err.println("[A2AHelpers] Missing required parameters. Received: " + result.toString());
            throw new IllegalArgumentException("Invalid input: 'agentUrl' and 'message' are required.");
        }

        return new A2ARequestInput(
                result.get("agentUrl").toString(),
                result.get("message").toString(),
                result.containsKey("taskId") ? result.get("taskId").toString() : null,
                result.containsKey("contextId") ? result.get("contextId").toString() : null);
    }

    public static String extractTextFromMessage(Message msg) {
        if (msg == null || msg.getParts() == null)
            return "";
        return extractTextFromParts(msg.getParts());
    }

    public static String extractTextFromParts(List<Part<?>> parts) {
        if (parts == null)
            return "";
        StringBuilder sb = new StringBuilder();
        for (Part<?> part : parts) {
            if (part instanceof TextPart textPart) {
                if (sb.length() > 0)
                    sb.append(" ");
                sb.append(textPart.getText());
            }
        }
        return sb.toString();
    }
}
