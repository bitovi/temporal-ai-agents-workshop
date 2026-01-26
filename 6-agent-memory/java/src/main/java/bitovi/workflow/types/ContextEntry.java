package bitovi.workflow.types;

import java.time.Instant;
import software.amazon.awssdk.services.bedrockagentcore.model.Role;

/**
 * Represents a single entry in the conversation context.
 * Each entry is structured data that can be persisted to AWS Bedrock Agent Core Memory
 * and formatted as XML for LLM consumption.
 * 
 * @param timestamp When the entry was created
 * @param role USER or ASSISTANT (from AWS SDK)
 * @param content Pure text content (without XML tags) - null for ACTION type
 * @param type The type of context entry (USER_MESSAGE, THOUGHT, ACTION, etc.)
 * @param actionReason Reasoning for an action (used only by ACTION type)
 * @param actionName Name of the action to execute (used only by ACTION type)
 * @param actionInput JSON input for the action (used only by ACTION type)
 */
public record ContextEntry(
    Instant timestamp,
    Role role,
    String content,
    ContextEntryType type,
    String actionReason,
    String actionName,
    String actionInput
) {
    /**
     * Formats this context entry as XML for LLM prompts.
     * The format varies by entry type.
     * 
     * @return XML string representation of this entry
     */
    public String toXmlString() {
        String dateStr = timestamp.toString();
        
        return switch (type) {
            case USER_MESSAGE -> String.format(
                "<user_message date=\"%s\">%s</user_message>",
                dateStr, content
            );
            
            case THOUGHT -> String.format(
                "<thought date=\"%s\">%s</thought>",
                dateStr, content
            );
            
            case ACTION -> String.format(
                "<action date=\"%s\"><reason>%s</reason><name>%s</name><input>%s</input></action>",
                dateStr, actionReason, actionName, actionInput
            );
            
            case OBSERVATION -> String.format(
                "<observation date=\"%s\">%s</observation>",
                dateStr, content
            );
            
            case ANSWER -> String.format(
                "<answer date=\"%s\">%s</answer>",
                dateStr, content
            );
            
            case SUMMARY -> String.format(
                "<summary date=\"%s\">%s</summary>",
                dateStr, content
            );
        };
    }
}
