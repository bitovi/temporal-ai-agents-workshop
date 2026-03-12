# Compacting Context and Continuing as New

##

```java
// Check if Temporal thinks we should continue-as-new
boolean shouldContinueAsNew = Workflow.getInfo().isContinueAsNewSuggested();

// Check if our Context is too large and we need to compress it
Integer contextLength = activities.getTokenUsage(context);
Workflow.getLogger(AgentMemoryWorkflowImpl.class).info("Current context token usage: " + contextLength);

if (shouldContinueAsNew || (contextLength != null && contextLength > COMPACTION_CONTEXT_TOKEN_THRESHOLD)) {
    CompactResponse compactResponse = activities.compactActivity(context);
    Workflow.continueAsNew(new WorkflowInput( new ContinueAsNewState(
            compactResponse.context(),
            usage,
            new ArrayList<>(pendingMsgs))));
}
```

## Compact Prompt

```xml
You are a summarization agent tasked with compacting the context of a ReAct (Reasoning and Acting) agent.

Your goal is to summarize the provided context, attempting to preserve the most important parts of the context history.

Instructions:
1. Review the provided context history.
2. Summarize the context, focusing on preserving key information and recent steps.
3. Ensure that the most recent parts of the context remain intact.

You do not need to include any XML tags such as <thought>, <action>, or <observation> in your response, those will be added automatically by the Agent Workflow.

Do not include any preamble, introductory remarks, explanations, or conversational filler. Output only the final result.

Here is the context history to be compacted:

<context-history>
{contextHistory}
</context-history>

Provide a compacted version of the context history, preserving important details and recent steps.
```

## Compaction Activity Implementation

```java
public static CompactResponse compactContext(String promptTemplate, List<ContextEntry> context) throws ApplicationFailure {
    try {
        String systemPrompt = promptTemplate
                .replace("{contextHistory}", String.join("\n", context));

        // Call Bedrock with low-quality model for cost optimization
        Config config = new Config();
        String modelId = config.getProperty("AWS_LOW_MODEL_ID");

        ModelResponseWithUsage response = BedrockConverse.bedrockConverseWithUsage(
                systemPrompt,
                List.of(new ChatMessage("user", systemPrompt)),
                null,
                modelId);

        // Create SUMMARY entry with compacted content
        ContextEntry summaryEntry = new ContextEntry(
                Instant.now(),
                Role.ASSISTANT,
                response.response(),
                ContextEntryType.SUMMARY);
        return new CompactResponse(summaryEntry, response.usage());
    } catch (Exception e) {
        throw ApplicationFailure.newFailure("compactContext failed: " + e.getMessage(),
                "CompactActivityError")
    }
}
```
