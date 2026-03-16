```java
public static void createEvent(List<ContextEntry> entries) {
    BedrockAgentCoreClient bedrockAgentCoreClient = AWS.getBedrockAgentCoreClient();

    List<PayloadType> payloads = entries.stream().map(entry -> {
        Conversational conversation = Conversational.builder()
                .content(Content.fromText(entry.toXMLString())) // Convert the entry to an XML string for storage
                .role(entry.role()).build(); // USER or ASSISTANT
            return PayloadType.builder().conversational(conversation).build();
        })
        .collect(Collectors.toList());

    // Use timestamp from first entry
    Instant eventTimestamp = entries.isEmpty() ? Instant.now() : entries.get(0).timestamp();
    CreateEventRequest request = CreateEventRequest.builder()
                    .memoryId(MEMORY_ID) // Our AgentCore Memory Resource in AWS
                    .sessionId(Activity.getExecutionContext().getInfo().getWorkflowId())
                    .actorId(USER_ID) // Associate this event with a specific user
                    .payload(payloads)
                    .eventTimestamp(eventTimestamp)
                    .build();

    bedrockAgentCoreClient.createEvent(request);
}
```

## Semantic Memory Resource

```java
public static CreateMemoryResponse createMemory() {
    BedrockAgentCoreControlClient controlClient = AWS.getBedrockAgentCoreControlClient()

    CreateMemoryRequest request = CreateMemoryRequest.builder()
                    .name(MEMORY_ID)
                    .description("This is an example that handles only Semantic Memories")
                    .eventExpiryDuration(30) // We can expire events after some number of days, if we want
                    .memoryStrategies(
                        MemoryStrategyInput.builder()
                            .semanticMemoryStrategy(
                                SemanticMemoryStrategyInput.builder()
                                    .name("Semantic")
                                    .description("Stores factual information and concepts")
                                    .namespaces(List.of("/strategies/{memoryStrategyId}/actors/{actorId}"))
                            .build())
                        .build())
                    .build();
    CreateMemoryResponse response = controlClient.createMemory(request);
    return response;
}
```

## User Preference Memory Resource

```java
public static CreateMemoryResponse createMemory() {
    BedrockAgentCoreControlClient controlClient = AWS.getBedrockAgentCoreControlClient()

    CreateMemoryRequest request = CreateMemoryRequest.builder()
                    .name(MEMORY_ID)
                    .description("This is an example that handles only User Preference Memories")
                    .eventExpiryDuration(30) // Events expire after 30 days
                    .memoryStrategies(
                        MemoryStrategyInput.builder()
                            .userPreferenceMemoryStrategy(
                                UserPreferenceMemoryStrategyInput.builder()
                                    .name("Preference")
                                    .description("Tracks user preferences and choices")
                                    .namespaces(List.of("/strategies/{memoryStrategyId}/actors/{actorId}"))
                                .build())
                        .build())
                    .build();
    CreateMemoryResponse response = controlClient.createMemory(request);
    return response;
}
```

## Episodic Memory Resource

```java
public static CreateMemoryResponse createMemory() {
    BedrockAgentCoreControlClient controlClient = AWS.getBedrockAgentCoreControlClient()

    CreateMemoryRequest request = CreateMemoryRequest.builder()
                    .name(MEMORY_ID)
                    .description("This is an example that handles only Episodic Memories")
                    .eventExpiryDuration(30) // Events expire after 30 days
                    .memoryStrategies(
                        MemoryStrategyInput.builder()
                            .episodicMemoryStrategy(
                                EpisodicMemoryStrategyInput.builder()
                                    .name("Episodic")
                                    .description("Stores temporal sequences of events")
                                    .namespaces(List.of("/strategies/{memoryStrategyId}/actors/{actorId}/sessions/{sessionId}"))
                                    .reflectionConfiguration(
                                        EpisodicReflectionConfigurationInput.builder()
                                            .namespaces(List.of("/strategies/{memoryStrategyId}/actors/{actorId}"))
                                        .build())
                                    .build())
                                .build())
                    .build();

    CreateMemoryResponse response = controlClient.createMemory(request);
    return response;
}
```

## Summary Memory

```java
public static CreateMemoryResponse createMemory() {
    BedrockAgentCoreControlClient controlClient = AWS.getBedrockAgentCoreControlClient()

    CreateMemoryRequest request = CreateMemoryRequest.builder()
        .name(MEMORY_ID)
        .description("This is an example that handles only Summary Memories")
        .eventExpiryDuration(30) // Events expire after 30 days
        .memoryStrategies(
            MemoryStrategyInput.builder()
                .summaryMemoryStrategy(
                    SummaryMemoryStrategyInput.builder()
                        .name("Summary")
                        .description("Maintains summarized conversation history")
                        .namespaces(List.of("/strategies/{memoryStrategyId}/actors/{actorId}/sessions/{sessionId}"))
                    .build())
                .build())
            .build();
    CreateMemoryResponse response = controlClient.createMemory(request);
    return response;
}
```

## Original Code

```java
public static CreateMemoryResponse createMemory() {
                try (BedrockAgentCoreControlClient controlClient = AWS.getBedrockAgentCoreControlClient()) {

                        CreateMemoryRequest request = CreateMemoryRequest.builder()
                                        .name(MEMORY_ID)
                                        .description(
                                                        "This is a temporary resource for the Temporal AI Agents Workshop (Part 2) delivered by Bitovi.")
                                        .eventExpiryDuration(30) // Events expire after 30 days
                                        .memoryStrategies(
                                                        MemoryStrategyInput.builder()
                                                                        .episodicMemoryStrategy(
                                                                                        EpisodicMemoryStrategyInput
                                                                                                        .builder()
                                                                                                        .name("Episodic")
                                                                                                        .description("Stores temporal sequences of events")
                                                                                                        .namespaces(List.of(
                                                                                                                        "/strategies/{memoryStrategyId}/actors/{actorId}/sessions/{sessionId}"))
                                                                                                        .reflectionConfiguration(
                                                                                                                        EpisodicReflectionConfigurationInput
                                                                                                                                        .builder()
                                                                                                                                        .namespaces(
                                                                                                                                                        List.of("/strategies/{memoryStrategyId}/actors/{actorId}"))
                                                                                                                                        .build())
                                                                                                        .build())
                                                                        .build(),
                                                        MemoryStrategyInput.builder()
                                                                        .userPreferenceMemoryStrategy(
                                                                                        UserPreferenceMemoryStrategyInput
                                                                                                        .builder()
                                                                                                        .name("Preference")
                                                                                                        .description("Tracks user preferences and choices")
                                                                                                        .namespaces(List.of(
                                                                                                                        "/strategies/{memoryStrategyId}/actors/{actorId}"))
                                                                                                        .build())
                                                                        .build(),
                                                        MemoryStrategyInput.builder()
                                                                        .semanticMemoryStrategy(
                                                                                        SemanticMemoryStrategyInput
                                                                                                        .builder()
                                                                                                        .name("Semantic")
                                                                                                        .description("Stores factual information and concepts")
                                                                                                        .namespaces(List.of(
                                                                                                                        "/strategies/{memoryStrategyId}/actors/{actorId}"))
                                                                                                        .build())
                                                                        .build(),
                                                        MemoryStrategyInput.builder()
                                                                        .summaryMemoryStrategy(
                                                                                        SummaryMemoryStrategyInput
                                                                                                        .builder()
                                                                                                        .name("Summary")
                                                                                                        .description("Maintains summarized conversation history")
                                                                                                        .namespaces(List.of(
                                                                                                                        "/strategies/{memoryStrategyId}/actors/{actorId}/sessions/{sessionId}"))
                                                                                                        .build())
                                                                        .build())
                                        .build();

                        System.out.println("Creating memory with request:");
                        System.out.println("  Name: " + request.name());
                        System.out.println("  Description: " + request.description());
                        System.out.println("  Event Expiry Duration: " + request.eventExpiryDuration() + " days");
                        System.out.println("  Number of strategies: " + request.memoryStrategies().size());

                        CreateMemoryResponse response = controlClient.createMemory(request);
                        return response;
                } catch (software.amazon.awssdk.services.bedrockagentcorecontrol.model.ValidationException e) {
                        System.err.println("\n=== Validation Error Creating Memory ===");
                        System.err.println("Error Message: " + e.getMessage());
                        System.err.println("Status Code: " + e.statusCode());
                        System.err.println("Request ID: " + e.requestId());
                        System.err.println("Service: " + e.awsErrorDetails().serviceName());
                        System.err.println("Error Code: " + e.awsErrorDetails().errorCode());
                        System.err.println("Error Message (detailed): " + e.awsErrorDetails().errorMessage());
                        if (e.awsErrorDetails().sdkHttpResponse() != null) {
                                System.err.println(
                                                "HTTP Status: " + e.awsErrorDetails().sdkHttpResponse().statusCode());
                        }
                        System.err.println("======================================\n");
                        throw e;
                } catch (Exception e) {
                        throw e;
                }
        }
```
