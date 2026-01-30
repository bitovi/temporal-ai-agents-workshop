package bitovi.activities.types;

import bitovi.workflow.types.UsageMetadata;

public record ThoughtResponse(
String type,
String thought,
String answer,
ActionDetail action,
UsageMetadata usage
) {
}
