package bitovi.common;

import java.util.ArrayList;
import java.util.List;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;

/**
 * Utility class for token estimation and context management.
 * Uses jtokkit for accurate token counting with character-based fallback.
 */
public class ModelUtils {

    private static final int CHARS_PER_TOKEN = 4;
    private static final int DEFAULT_MAX_TOKENS = 12000;
    private static final EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
    private static final Encoding encoding = registry.getEncoding(EncodingType.CL100K_BASE);

    /**
     * Count the number of tokens in a text string.
     * Uses jtokkit for accurate token counting, with character-based heuristic as
     * fallback.
     * 
     * @param text The text to count tokens for
     * @return Token count
     */
    public static int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        try {
            // Use jtokkit for accurate token counting
            return encoding.countTokens(text);
        } catch (Exception e) {
            // Fallback to character-based heuristic if jtokkit fails
            System.out.println(
                    "Warning: jtokkit token counting failed, falling back to character-based heuristic. Error: "
                            + e.getMessage());
            return text.length() / CHARS_PER_TOKEN;
        }
    }

    /**
     * Truncate a context list to fit within a token limit.
     * Keeps the most recent entries (from the end of the list).
     * 
     * @param context   List of context strings
     * @param maxTokens Maximum tokens to keep
     * @return Truncated list with most recent entries
     */
    public static List<String> truncateContextToTokenLimit(List<String> context, int maxTokens) {
        if (context == null || context.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> result = new ArrayList<>();
        int totalTokens = 0;

        // Traverse backwards to keep most recent entries
        for (int i = context.size() - 1; i >= 0; i--) {
            String entry = context.get(i);
            int entryTokens = estimateTokenCount(entry);

            if (totalTokens + entryTokens > maxTokens) {
                // Would exceed limit, stop here
                break;
            }

            result.add(0, entry); // Add at beginning to maintain order
            totalTokens += entryTokens;
        }

        return result;
    }

    /**
     * Truncate context using the default max tokens from Config.
     * 
     * @param context List of context strings
     * @return Truncated list with most recent entries
     */
    public static List<String> truncateContextToTokenLimit(List<String> context) {
        return truncateContextToTokenLimit(context, MAX_CONTEXT_TOKENS());
    }

    public static int MAX_CONTEXT_TOKENS() {
        Config config = new Config();
        String maxTokensStr = config.getNullableProperty("MAX_CONTEXT_TOKENS");
        return maxTokensStr != null ? Integer.parseInt(maxTokensStr) : DEFAULT_MAX_TOKENS;
    }
}
