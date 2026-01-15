package bitovi.activities.DTO;

import bitovi.UsageMetadata;

public record ThoughtResponse(
String type,
String thought,
String answer,
ActionDetail action,
UsageMetadata usage
) {
}
