package bitovi.activities.types;

import bitovi.workflow.types.UsageMetadata;

/**
 * Response from the thought activity.
 *
 * The LLM returns one of two types:
 *   type="answer" — thought + answer (final response to the user)
 *   type="action" — thought + action (tool to invoke next)
 */
public record ThoughtResponse(
	String type,
	String thought,
	String answer,
	ActionDetail action,
	UsageMetadata usage
) {
}
