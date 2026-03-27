# Custom Implementation

## Custom Memory Implementation Workflow

```java
public class MemoryExtractionWorkflowImpl implements MemoryExtractionWorkflow {
    private final List<MemoryExtractionEventInput> pending = new ArrayList<>();

    private String userId;

    public void receiveMessage(MemoryExtractionEventInput event) {
        pending.add(event);
    }

    public void execute(MemoryExtractionWorkflowInput input) {
        this.userId = input.userId();

        // Process the pending context entries
        while (!pending.isEmpty()) {
            List<MemoryExtractionEventInput> eventsToProcess = new ArrayList<>(pending);

            for (MemoryExtractionEventInput event : eventsToProcess) {
                activities.extractSemanticMemories(userId, event.sessionId(), event.entries());
            }
        }
    }
}
```

## Chat Workflow Snippit

```java
if (response.type().equals("answer")) {
    ContextEntry answer = ContextEntry.fromAnswer(response.answer());
    persist.add(answer);

    // Collect entries from most recent USER_MESSAGE to ANSWER
    activities.persistMemoryActivity(persist, Workflow.getInfo().getRunId());

    // The rest of the workflow continues here
}
```

## Local Memory SignalWithStart

```java
public static void createEvent(List<ContextEntry> entries, String sessionId) {
    WorkflowClient temporalClient = TemporalClient.getTemporalClient();
    String workflowId = "extract-memory-" + USER_ID;

    WorkflowOptions workflowOptions = WorkflowOptions
                    .newBuilder()
                    .setTaskQueue(TEMPORAL_TASK_QUEUE)
                    .setWorkflowId(workflowId)
                    .build();

    MemoryExtractionWorkflow workflow = temporalClient
                    .newWorkflowStub(MemoryExtractionWorkflow.class, workflowOptions);

    WorkflowStub.fromTyped(workflow).signalWithStart(
                    "event",
                    new Object[] { new MemoryExtractionEventInput(entries, sessionId) },
                    new Object[] { new MemoryExtractionWorkflowInput(USER_ID) });
}
```

## Chat Workflow Persist Memory Activity

```java
public void persistMemoryActivity(List<ContextEntry> entries, String sessionId) {
    // Save the messages to the local database in raw form.
    try {
        RawEventHelper.persistSessionEventsImpl(sessionId, entries);
    } catch (InterruptedException | ExecutionException e) {
        throw ApplicationFailure.newFailure("Failed:" + e.getMessage(), "PersistChatEventsError");
    }

    // Send the messages to the local memory extraction system.
    try {
        LocalMemory.createEvent(entries, sessionId);
    } catch (Exception e) {
        throw ApplicationFailure.newFailure("Failed:" + e.getMessage(), "CreateEventError");
    }
}
```

## Extraction Workflow Semantic Activity

```java
public void extractSemanticMemoriesImpl(String sessionId, List<ContextEntry> entries) {
    List<String> contextStrings = toXMLString(entries);

    // 1. Extract semantic memories from the context strings
    List<SemanticMemoryRecord> records = extractionStep(sessionId, contextStrings);

    // 2. Consolidate the extracted semantic memories into actions
    List<SemanticMemoryAction> actions = consolidationStep(records);

    for (SemanticMemoryAction action : actions) {
        SemanticMemoryRecord record = action.memory();

        // 3. Convert and store the semantic memory records in the database
        List<Float> vectorData = BedrockEmbed.calculateEmbedding(record.fact());

        PointStruct upsertInput = createVectorUpsertAsyncInput(record, vectorData);
        vectorDatabase.upsertAsync(upsertInput);
    }
}
```

## Semantic Extraction Prompt

```xml
You are a long-term memory extraction agent supporting a lifelong learning system. Your task is to identify and extract meaningful information about the users from a given list of messages.

<past_conversation>
{pastConversation}
</past_conversation>

<current_conversation>
{currentConversation}
</current_conversation>

Analyze the conversation and extract structured information about the user according to the schema below. Only include details that are explicitly stated or can be logically inferred from the conversation.

- Extract information ONLY from the user messages. You should use assistant messages only as supporting context.
- If the conversation contains no relevant or noteworthy information, return an empty list.
- Do NOT extract anything from prior conversation history, even if provided. Use it solely for context.
- Do NOT incorporate external knowledge.
- Avoid duplicate extractions.

IMPORTANT: Maintain the original language of the user's conversation. If the user communicates in a specific language, extract and format the extracted information in that same language.

Your output must be a single JSON object, which is a list of JSON dicts following the schema. Do not provide any preamble or any explanatory text.
```

## Semantic Extraction Schema

```xml
<schema>
{
  "description": "This is a standalone personal fact about the user, stated in a simple sentence.\\nIt should represent a piece of personal information, such as life events, personal experience, and preferences related to the user.\\nMake sure you include relevant details such as specific numbers, locations, or dates, if presented.\\nMinimize the coreference across the facts, e.g., replace pronouns with actual entities.",
  "properties": {
    "fact": {
      "description": "The memory as a well-written, standalone fact about the user. Refer to the user's instructions for more information the prefered memory organization.",
      "title": "Fact",
      "type": "string"
    }
  },
  "required": [
    "fact"
  ],
  "title": "SemanticMemory",
  "type": "object"
}
</schema>
```

## Semantic Extraction Function

```java
private List<SemanticMemoryRecord> extractionStep(String sessionId, List<String> currentConversation) {
    List<ContextEntry> pastChatEvents = RawEventHelper.fetchRawChatEvents(sessionId);
    List<String> pastConversation = toXMLStrings(pastChatEvents);

    String systemPrompt = extractTemplate
            .replace("{pastConversation}", String.join("\n", pastConversation))
            .replace("{currentConversation}", String.join("\n", currentConversation));

    ModelResponse result = BedrockConverse.bedrockConverseWithUsage(
            systemPrompt,
            List.of(new ChatMessage("user", "Perform the semantic memory extraction.")),
            AWS_MODEL_ID);

    List<SemanticMemoryRecord> records = new ArrayList<>();

    JSONArray jsonArray = new JSONArray(result.response());
    for (int i = 0; i < jsonArray.length(); i++) {
        JSONObject jsonObject = jsonArray.getJSONObject(i);
        records.add(SemanticMemoryRecord.fromJson(jsonObject));
    }
    return records;
}
```

## Semantic Consolidation Prompt

```xml
You are a conservative memory manager that preserves existing information while carefully integrating new facts.

Your operations are:
- **AddMemory**: Create new memory entries for genuinely new information
- **UpdateMemory**: Add complementary information to existing memories while preserving original content
- **SkipMemory**: No action needed (information already exists or is irrelevant)

If the operation is "AddMemory", you need to output:
1. The `memory` field with the new memory content

If the operation is "UpdateMemory", you need to output:
1. The `memory` field with the original memory content
2. The update_id field with the ID of the memory being updated
3. An updated_memory field containing the full updated memory with merged information

Return only this JSON structure, using double quotes for all keys and string values:
```

## Semantic Consolidation Schema

```xml
[
  {
    "memory": {
      "fact": "<content>"
    },
    "operation": "<AddMemory_or_UpdateMemory>",
    "update_id": "<existing_id_for_UpdateMemory>",
    "updated_memory": {
      "fact": "<content>"
    }
  },
  ...
]

Only include entries with AddMemory or UpdateMemory operations. Return empty memory array if no changes are needed.
Do not return anything except the JSON format.

Here are the memories to evaluate along with their relevant existing memories:
{memories}
```

## Semantic Consolidation Implementation

```java
private List<SemanticMemoryAction> consolidationStep(List<SemanticMemoryRecord> records) {
    List<String> memoryStrings = new ArrayList<>();

    for (SemanticMemoryRecord record : records) {
        List<SemanticMemoryRecord> relatedMemories = findRelatedMemories(record);
        memoryStrings.add(record.toRelationXML(relatedMemories));
    }

    String systemPrompt = consolidateTemplate.replace("{memories}", String.join("\n", memoryStrings));

    ModelResponse result = BedrockConverse.bedrockConverseWithUsage(
            systemPrompt,
            List.of(new ChatMessage("user", "Perform the semantic memory consolidation.")),
            AWS_MODEL_ID);

    JSONArray jsonArray = new JSONArray(result.response());
    List<SemanticMemoryAction> actions = new ArrayList<>();
    for (int i = 0; i < jsonArray.length(); i++) {
        JSONObject obj = jsonArray.getJSONObject(i);
        actions.add(SemanticMemoryAction.fromJson(obj));
    }

    return actions;
}
```

## Semantic Pipeline Completion

```java
List<SemanticMemoryAction> actions = consolidationStep(records);

for (SemanticMemoryAction action : actions) {
    SemanticMemoryRecord record = action.memory();
    List<Float> vector = BedrockEmbed.calculateEmbedding(record.fact());

    PointStruct ps = PointStruct.newBuilder()
            .setId(id(record.id()))
            .setVectors(vectors(vector))
            .putAllPayload(record.vectorPayload())
            .build();

    vectorDatabase.upsertAsync(List.of(ps));
}
```