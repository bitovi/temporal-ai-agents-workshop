package bitovi.common;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for token estimation and context management.
 * Uses simple character-based estimation (1 token ≈ 4 characters).
 */
public class ModelUtils {
    
    private static final int CHARS_PER_TOKEN = 4;
    private static final int DEFAULT_MAX_TOKENS = 12000;

    /**
     * Estimate the number of tokens in a text string.
     * Uses a simple heuristic: 1 token ≈ 4 characters
     * 
     * @param text The text to estimate tokens for
     * @return Estimated token count
     */
    public static int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.length() / CHARS_PER_TOKEN;
    }

    /**
     * Truncate a context list to fit within a token limit.
     * Keeps the most recent entries (from the end of the list).
     * 
     * @param context List of context strings
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
        Config config = new Config();
        String maxTokensStr = config.getProperty("MAX_CONTEXT_TOKENS");
        int maxTokens = maxTokensStr != null ? Integer.parseInt(maxTokensStr) : DEFAULT_MAX_TOKENS;
        return truncateContextToTokenLimit(context, maxTokens);
    }
}
