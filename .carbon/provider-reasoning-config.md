# Model Provider Reasoning Effort

## AWS Bedrock

```java
Document reasoningConfig = Document.mapBuilder()
        .putDocument("reasoningConfig", Document.mapBuilder()
                .putString("type", "enabled")
                .putString("maxReasoningEffort", "low") // low, medium, high
                .build())
        .build();

requestBuilder.additionalModelRequestFields(reasoningConfig);
```

## OpenAI

```java
ChatCompletionRequest request = new ChatCompletionRequest.Builder()
        .model("gpt-5.1") // Must be a reasoning model
        .messages(List.of(new ChatCompletionResponseMessage.Builder()
                .role("user")
                .content("Explain the theory of relativity in simple terms.")
                .build()))
        // Set the reasoning effort parameter
        .reasoningEffort(ReasoningEffort.HIGH) // Or LOW, MEDIUM, XHIGH, etc.
        .build();
```
