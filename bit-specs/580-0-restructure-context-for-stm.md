# Restructure Context for Short-Term Memory (STM)

We need to restructure the "context" state in [AgentMemoryWorkflowImpl.java](../6-agent-memory/java/src/main/java/bitovi/workflow/AgentMemoryWorkflowImpl.java) to support structured data for AWS Bedrock Agent Core Memory.

## Purpose
Enable uploading context data to AWS Bedrock Agent Core Memory as structured Event records rather than XML-formatted strings.

## Background
Currently, the context is a `List<String>` with XML-like formatting (e.g., `<user_message>`, `<thought>`, `<action>`, `<observation>`, `<answer>`). While this format works for LLM consumption, it's insufficient for creating structured Event data for AWS Bedrock Agent Core Memory short-term memory resources.

The current flow:
- User messages are formatted with XML tags and added to context
- ReAct loop elements (thoughts, actions, observations, answers) are also XML-formatted
- Context is only used for LLM prompts and compaction
- Separately, `persistMemoryActivity` calls `AgentCoreMemory.createEvent()` to store events

## Requirements
- Replace `List<String> context` with `List<ContextEntry> context` where `ContextEntry` is a structured record/POJO
- Each `ContextEntry` must include:
  - **timestamp**: `Instant` - when the entry was created (using `Instant.now()`)
  - **role**: `Role` enum - USER or ASSISTANT (from AWS SDK: `software.amazon.awssdk.services.bedrockagentcore.model.Role`)
  - **content**: `String` - pure text content (message text, thought, observation, etc. without XML tags)
  - **type**: `ContextEntryType` enum - USER_MESSAGE, THOUGHT, ACTION, OBSERVATION, ANSWER, SUMMARY
  - **actionReason**, **actionName**, **actionInput**: `String` fields used only by ACTION type (null for other types)
- `ContextEntry` provides:
  - Method `toXmlString()` to format the entry as XML for LLM prompts (generates `<user_message>`, `<thought>`, `<action>`, etc.)

## Implementation Plan

### Step 1: Create the ContextEntry Record and Type Enum
**Location**: `6-agent-memory/java/src/main/java/bitovi/workflow/types/`

Create `ContextEntryType.java`:
- Define enum with values: `USER_MESSAGE`, `THOUGHT`, `ACTION`, `OBSERVATION`, `ANSWER`, `SUMMARY`

Create `ContextEntry.java`:
- Define record with fields: `timestamp`, `role`, `content`, `type`, `actionReason`, `actionName`, `actionInput`
- Content field contains pure text for most types (no XML tags)
- For ACTION type: content is null, use actionReason/actionName/actionInput instead
- actionReason, actionName, actionInput are only used by ACTION type (null for other types)
- Required imports:
  ```java
  import java.time.Instant;
  import software.amazon.awssdk.services.bedrockagentcore.model.Role;
  ```
- Add method `toXmlString()` that formats the entry as XML based on type:
  - USER_MESSAGE → `<user_message date="...">content</user_message>` (timestamp formatted using `timestamp.toString()`)
  - THOUGHT → `<thought date="...">content</thought>` (timestamp formatted using `timestamp.toString()`)
  - ACTION → `<action date="..."><reason>...</reason><name>...</name><input>...</input></action>` (timestamp formatted using `timestamp.toString()`, using actionReason, actionName, actionInput fields)
  - OBSERVATION → `<observation date="...">content</observation>` (timestamp formatted using `timestamp.toString()`)
  - ANSWER → `<answer date="...">content</answer>` (timestamp formatted using `timestamp.toString()`)
  - SUMMARY → `<summary date="...">content</summary>` (timestamp formatted using `timestamp.toString()`)

**Verification**: 
- Run the "Exercise 6 Maven Build" task and verify compilation succeeds

### Step 2: Update ContinueAsNewState to Use ContextEntry
**Location**: `6-agent-memory/java/src/main/java/bitovi/workflow/types/ContinueAsNewState.java`

Change the record definition:
- Replace `List<String> context` with `List<ContextEntry> context`

**Verification**:
- Run the "Exercise 6 Maven Build" task and verify compilation succeeds

### Step 3: Update WorkflowInput to Use ContextEntry
**Location**: `6-agent-memory/java/src/main/java/bitovi/workflow/types/WorkflowInput.java`

If `WorkflowInput` contains a context field, update it similarly.

**Verification**:
- Run the "Exercise 6 Maven Build" task and verify compilation succeeds

### Step 4: Update AgentMemoryWorkflowImpl - Initialize Context
**Location**: `6-agent-memory/java/src/main/java/bitovi/workflow/AgentMemoryWorkflowImpl.java`

Update the execute method initialization:
- Change `List<String> context` to `List<ContextEntry> context`
- Update initialization from `input.continueAsNew().context()` to handle new type

**Verification**:
- Run the "Exercise 6 Maven Build" task and verify compilation succeeds

### Step 5: Update User Message Processing
**Location**: `6-agent-memory/java/src/main/java/bitovi/workflow/AgentMemoryWorkflowImpl.java` (lines ~120-130)

Replace the current user message formatting:
```java
String userMessage = String.format("<user_message name=\"%s\" date=\"%s\">\n%s\n</user_message>",
    msg.name(), msg.date(), msg.message());
context.add(userMessage);
```

With:
```java
ContextEntry userEntry = new ContextEntry(
    Instant.now(),  // Use current timestamp
    Role.USER,
    msg.message(),  // Pure content, no XML tags
    ContextEntryType.USER_MESSAGE,
    null,  // actionReason - not used for USER_MESSAGE
    null,  // actionName - not used for USER_MESSAGE
    null   // actionInput - not used for USER_MESSAGE
);
context.add(userEntry);
```

### Step 6: Update Thought Processing (Answer Branch)
**Location**: `6-agent-memory/java/src/main/java/bitovi/workflow/AgentMemoryWorkflowImpl.java` (lines ~153-162)

Replace:
```java
String answerContext = String.format("<answer>\n%s\n</answer>", thoughtResponse.answer());
context.add(answerContext);
```

With:
```java
ContextEntry answerEntry = new ContextEntry(
    Instant.now(),
    Role.ASSISTANT,
    thoughtResponse.answer(),  // Pure content
    ContextEntryType.ANSWER,
    null,  // actionReason - not used for ANSWER
    null,  // actionName - not used for ANSWER
    null   // actionInput - not used for ANSWER
);
context.add(answerEntry);
```

### Step 7: Update Thought Processing (Action Branch)
**Location**: `6-agent-memory/java/src/main/java/bitovi/workflow/AgentMemoryWorkflowImpl.java` (lines ~167-184)

Replace thought formatting:
```java
String thoughtContext = String.format("<thought>\n%s\n</thought>", thoughtResponse.thought());
context.add(thoughtContext);
```

With:
```java
ContextEntry thoughtEntry = new ContextEntry(
    Instant.now(),
    Role.ASSISTANT,
    thoughtResponse.thought(),  // Pure content
    ContextEntryType.THOUGHT,
    null,  // actionReason - not used for THOUGHT
    null,  // actionName - not used for THOUGHT
    null   // actionInput - not used for THOUGHT
);
context.add(thoughtEntry);
```

Replace action formatting:
```java
String actionContext = String.format(
    "<action><reason>\n%s\n</reason><name>%s</name><input>%s</input></action>",
    action.reason(), action.name(), actionInputJson);
context.add(actionContext);
```

With:
```java
ContextEntry actionEntry = new ContextEntry(
    Instant.now(),
    Role.ASSISTANT,
    null,  // ACTION type uses dedicated fields instead of content
    ContextEntryType.ACTION,
    null,  // No metadata needed for ACTION
    action.reason(),      // actionReason field
    action.name(),        // actionName field
    actionInputJson       // actionInput field
);
context.add(actionEntry);
```

### Step 8: Update Observation Processing
**Location**: `6-agent-memory/java/src/main/java/bitovi/workflow/AgentMemoryWorkflowImpl.java` (lines ~194-198)

Replace:
```java
String observationContext = String.format("<observation>\n%s\n</observation>",
    observationResponse.observations());
context.add(observationContext);
```

With:
```java
ContextEntry observationEntry = new ContextEntry(
    Instant.now(),
    Role.ASSISTANT,
    observationResponse.observations(),  // Pure content
    ContextEntryType.OBSERVATION,
    null,  // actionReason - not used for OBSERVATION
    null,  // actionName - not used for OBSERVATION
    null   // actionInput - not used for OBSERVATION
);
context.add(observationEntry);
```

**Verification for Steps 5-8**:
- Run the "Exercise 6 Maven Build" task and verify compilation succeeds
- All context entry types (USER_MESSAGE, THOUGHT, ACTION, OBSERVATION, ANSWER) are now structured
- Cannot fully test until Activity methods (Step 9) are also updated to handle ContextEntry list

### Step 9: Update Activity Methods to Accept ContextEntry List
**Location**: 
- `6-agent-memory/java/src/main/java/bitovi/activities/Activities.java`
- `6-agent-memory/java/src/main/java/bitovi/activities/ActivitiesImpl.java`

Update method signatures that currently accept `List<String> context`:
- `thoughtActivity(List<ContextEntry> context)`
- `observationActivity(List<ContextEntry> context, String actionResult)`
- `compactActivity(List<ContextEntry> context)`

Update implementations to convert context entries to XML for prompts:
```java
String contextString = context.stream()
    .map(ContextEntry::toXmlString)
    .collect(Collectors.joining("\n"));
```

**Verification**:
- Run the "Exercise 6 Maven Build" task and verify all activities compile successfully
- LLM prompts still receive proper XML-formatted context
- Activities return expected responses

### Step 10: Update CompactResponse to Use ContextEntry
**Location**: `6-agent-memory/java/src/main/java/bitovi/activities/types/CompactResponse.java`

Update the record:
- Change `List<String> context` to `List<ContextEntry> context`

Update compaction logic in `ActivitiesImpl.java`:
- Wrap compacted response in a single `ContextEntry` with current timestamp
- Use ContextEntryType.SUMMARY type
- Content is the pure compacted text (all compressed content in one entry)
- Return the SUMMARY entry PLUS the last 3 original context entries (preserve recent context)
- SUMMARY type can also be used for LTM summaries from AWS Bedrock Agent Core Memory

**Verification**:
- Compaction workflow succeeds
- Context is properly restored after compaction
- Continue-as-new works with new structure

### Step 11: Enhance persistMemoryActivity to Use ContextEntry
**Location**: 
- `6-agent-memory/java/src/main/java/bitovi/activities/ActivitiesImpl.java`
- `6-agent-memory/java/src/main/java/bitovi/common/aws/AgentCoreMemory.java`

Update `persistMemoryActivity`:
- Change signature to accept `List<ContextEntry>` instead of `String memoryText` and `Role role`
- Call `AgentCoreMemory.createEvent(List<ContextEntry> entries)`

Update `AgentCoreMemory.createEvent`:
- Accept `List<ContextEntry>` parameter
- Create a `Conversational` object for each ContextEntry in the list:
  - Use `Content.fromText(entry.toXmlString())` for content (XML-formatted)
  - Set role from `entry.role()` (each Conversational has its own role)
  - Note: For ACTION type entries, `toXmlString()` will generate the complete action XML
- Build collection of `PayloadType` objects using `PayloadType.builder().conversational(conversational).build()`
- Pass the collection to CreateEventRequest using `.payload(Collection<PayloadType> payload)` builder method
- Set actorId and sessionId from workflow context
- Use eventTimestamp from the first entry's timestamp

Update workflow to pass context entries:
- Remove the immediate persistence call for user messages: `activities.persistMemoryActivity(msg.message(), Role.USER)`
- Remove all calls to the old `persistActivity(List<PersistMessage> messages)` method (file-based logging)
- Persistence will now only happen after an answer is generated (see Step 12)
- Both USER and ASSISTANT role entries can be included in the same Event (each has its own role in the Conversational payload)

**Verification**:
- Events are created in AWS Bedrock with proper timestamps
- Role is correctly set (USER vs ASSISTANT)
- Content contains XML from multiple context entries
- Event structure is valid

### Step 12: Persist Context Entries After Answer
**Location**: `6-agent-memory/java/src/main/java/bitovi/workflow/AgentMemoryWorkflowImpl.java`

Update the workflow to persist context entries only after an answer is generated:
- After an ANSWER entry is added to context, identify entries from the latest user question → answer cycle
- Find the most recent USER_MESSAGE entry in the context
- Collect all entries from that USER_MESSAGE to the current ANSWER entry (inclusive)
- Call `activities.persistMemoryActivity(entriesToPersist)` with the collected entries
- This batches all context entries from the complete conversation turn:
  - Single-turn: USER_MESSAGE → ANSWER
  - Multi-turn with tool usage: USER_MESSAGE → THOUGHT → ACTION → OBSERVATION → THOUGHT → ... → ANSWER
- Both USER and ASSISTANT role entries are included in the same AWS Event (each Conversational payload has its own role)

**Implementation Approach**:
```java
// After adding answerEntry to context
List<ContextEntry> entriesToPersist = new ArrayList<>();
boolean foundUserMessage = false;

// Iterate backwards to find the most recent USER_MESSAGE
for (int i = context.size() - 1; i >= 0; i--) {
    ContextEntry entry = context.get(i);
    entriesToPersist.add(0, entry);  // Add at beginning to maintain order
    
    if (entry.type() == ContextEntryType.USER_MESSAGE) {
        foundUserMessage = true;
        break;
    }
}

if (foundUserMessage) {
    activities.persistMemoryActivity(entriesToPersist);
}
```

**Verification**:
- AWS Bedrock memory contains all relevant conversation elements
- Persistence only happens after answer generation, not for individual messages
- Query memory using `listEvents()` shows proper event history
- Each Event contains multiple context entries (complete conversation turns)
- Memory can be used for context retrieval
- No file-based persistence remains in the code



### Step 13: Integration Testing
**Location**: Test the complete workflow end-to-end

Test scenarios:
1. Start new workflow, send user message, get answer
2. Send user message requiring tool call, verify action execution
3. Trigger compaction, verify context preserved
4. Continue-as-new with context, verify state restored
5. Query AWS memory, verify events match context entries

**Verification**:
- All test scenarios pass
- No data loss during context operations
- AWS memory accurately reflects conversation history
- Timestamps are consistent and correct

---

## Design Decisions (Resolved)

The following design questions were raised and resolved:

1. **Timestamps**: All ContextEntry timestamps will use `Instant.now()` for consistency and simplicity
2. **Metadata Field**: Removed - no use case identified; USER_MESSAGE content is sufficient
3. **ContextEntry Design**: Using record with optional fields (actionReason/actionName/actionInput for ACTION type); can refactor later if needed
4. **Persistence Strategy**: 
   - Remove all file-based `persistActivity()` calls (old console logging)
   - Persist to AWS Bedrock only, after ANSWER is generated
   - Batch entire conversation turn (USER_MESSAGE → ANSWER with any intermediate steps)
   - Temporal's durable execution protects against data loss
5. **Compaction Output**: Returns SUMMARY entry + last 3 original entries (preserves recent context)
6. **AWS SDK Usage**: 
   - Each ContextEntry maps to a Conversational payload with its own role
   - Single Event contains multiple PayloadType objects using `.payload(Collection<PayloadType>)` builder
   - Event has actorId but no overall role field
7. **XML Formatting**: Use `timestamp.toString()` for date attributes in XML tags

## Questions

### 1. Compaction Implementation Detail
When creating the SUMMARY ContextEntry in Step 10, should we:
- Use `Role.ASSISTANT` for the summary entry?
- Set actionReason/actionName/actionInput to null?
- What timestamp should be used - the timestamp of compaction execution (`Instant.now()`)?

### 2. Multiple USER_MESSAGE Handling
In Step 12, if multiple pending messages are processed before an answer (user sends multiple messages in quick succession), should we:
- Collect from the FIRST USER_MESSAGE to ANSWER (all pending messages)?
- Only collect from the LAST USER_MESSAGE to ANSWER (most recent question)?
- Current spec implies collecting from most recent USER_MESSAGE backward - is this correct?

### 3. Activities Interface Cleanup
Should we also:
- Remove the `persistActivity(List<PersistMessage>)` method entirely from Activities.java and ActivitiesImpl.java?
- Remove the PersistMessage record class since it's no longer used?

### 4. Error Handling
Should Step 11 (AgentCoreMemory.createEvent) include error handling for:
- Empty context entry list?
- Network failures when calling AWS Bedrock?
- Invalid content in ContextEntry fields?

### 5. Continue-As-New Edge Case
After compaction and continue-as-new, if there are pending messages in ContinueAsNewState:
- Should those pending messages be processed before considering another compaction?
- The spec says compaction happens when `pendingMsgs.isEmpty()` - does this work correctly?
