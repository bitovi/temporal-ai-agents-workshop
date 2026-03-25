package bitovi.workflow.types;

import java.time.Instant;

import software.amazon.awssdk.services.bedrockagentcore.model.Role;

/**
 * Represents a single entry in the conversation context.
 * Each entry is structured data that can be persisted to AWS Bedrock Agent Core
 * Memory
 * and formatted as XML for LLM consumption.
 * 
 * @param timestamp    When the entry was created
 * @param role         USER or ASSISTANT (from AWS SDK)
 * @param content      Pure text content (without XML tags) - null for ACTION
 *                     type
 * @param type         The type of context entry (USER_MESSAGE, THOUGHT, ACTION,
 *                     etc.)
 * @param actionReason Reasoning for an action (used only by ACTION type)
 * @param actionName   Name of the action to execute (used only by ACTION type)
 * @param actionInput  JSON input for the action (used only by ACTION type)
 */
public record ContextEntry(
        Instant timestamp,
        Role role,
        String content,
        ContextEntryType type,
        String actionReason,
        String actionName,
        String actionInput) {

    public static ContextEntry fromSummary(String content) {
        return new ContextEntry(
                Instant.now(),
                Role.ASSISTANT,
                content,
                ContextEntryType.SUMMARY,
                null,
                null,
                null);
    }

    public static ContextEntry fromThought(String content) {
        return new ContextEntry(
                Instant.now(),
                Role.ASSISTANT,
                content,
                ContextEntryType.THOUGHT,
                null,
                null,
                null);
    }

    public static ContextEntry fromUser(String content, Instant timestamp) {
        return new ContextEntry(
                timestamp,
                Role.USER,
                content,
                ContextEntryType.USER_MESSAGE,
                null,
                null,
                null);
    }

    public static ContextEntry fromUser(String content) {
        return new ContextEntry(
                Instant.now(),
                Role.USER,
                content,
                ContextEntryType.USER_MESSAGE,
                null,
                null,
                null);
    }

    public static ContextEntry fromAnswer(String content, Instant timestamp) {
        return new ContextEntry(
                timestamp,
                Role.ASSISTANT,
                content,
                ContextEntryType.ANSWER,
                null,
                null,
                null);
    }

    public static ContextEntry fromAnswer(String content) {
        return new ContextEntry(
                Instant.now(),
                Role.ASSISTANT,
                content,
                ContextEntryType.ANSWER,
                null,
                null,
                null);
    }

    public static ContextEntry fromAction(String reason, String name, String input) {
        return new ContextEntry(
                Instant.now(),
                Role.ASSISTANT,
                null,
                ContextEntryType.ACTION,
                reason,
                name,
                input);
    }

    public static ContextEntry fromObservation(String content) {
        return new ContextEntry(
                Instant.now(),
                Role.ASSISTANT,
                content,
                ContextEntryType.OBSERVATION,
                null,
                null,
                null);
    }

    /**
     * Formats this context entry as XML for LLM prompts.
     * The format varies by entry type.
     * 
     * @return XML string representation of this entry
     */
    public String toXMLString() {
        String dateStr = timestamp.toString();

        return switch (type) {
            case USER_MESSAGE -> String.format(
                    "<user_message date=\"%s\">%s</user_message>",
                    dateStr, content);

            case THOUGHT -> String.format(
                    "<thought date=\"%s\">%s</thought>",
                    dateStr, content);

            case ACTION -> String.format(
                    "<action date=\"%s\"><reason>%s</reason><name>%s</name><input>%s</input></action>",
                    dateStr, actionReason, actionName, actionInput);

            case OBSERVATION -> String.format(
                    "<observation date=\"%s\">%s</observation>",
                    dateStr, content);

            case ANSWER -> String.format(
                    "<answer date=\"%s\">%s</answer>",
                    dateStr, content);

            case SUMMARY -> String.format(
                    "<summary date=\"%s\">%s</summary>",
                    dateStr, content);
        };
    }
}
