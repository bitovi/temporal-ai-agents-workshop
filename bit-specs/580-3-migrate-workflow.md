# SYSTEMS-580-3: Migrate Workflow

This is a subsection of [580-0-convert-ts-to-java.md](./580-0-convert-ts-to-java.md)
Include that in these instructions.

## Overview

Migrate the agent workflow from [temp-ref-code/src/workflows/workflow.ts](../temp-ref-code/src/workflows/workflow.ts) into the Java implementation at [5-agent-workflow/java/src/main/java/bitovi/AgentWorkflowImpl.java](../5-agent-workflow/java/src/main/java/bitovi/AgentWorkflowImpl.java). This workflow implements an autonomous agent that can:
- Receive messages via signals
- Process messages through a think-act-observe loop
- Execute tools/actions based on AI decisions
- Support continue-as-new for long-running conversations
- Track usage metrics across workflow executions
- Handle graceful exit signals

## Context

The TypeScript reference workflow in [temp-ref-code/src/workflows/workflow.ts](../temp-ref-code/src/workflows/workflow.ts) is a conversational agent that:
- Uses two signals: `agentWorkflowMessageSignal` for incoming messages and `agentWorkflowExitSignal` to terminate
- Maintains state including context history, usage metrics, and pending messages
- Implements an event loop that waits for messages, processes them through activities, and continues
- Uses five activities: `thoughtEntity`, `actionEntity`, `observationEntity`, `compactEntity`, and `persistEntity`
- Supports continue-as-new when the workflow history becomes too large
- Accumulates usage metrics (token counts) across continue-as-new boundaries

The Java implementation at [AgentWorkflowImpl.java](../5-agent-workflow/java/src/main/java/bitovi/AgentWorkflowImpl.java) currently has a stubbed implementation with commented-out code from a simpler pattern.

## Current State

### What Exists
- [AgentWorkflow.java](../5-agent-workflow/java/src/main/java/bitovi/AgentWorkflow.java) - Workflow interface with simple `String execute()` method ⚠️
- [AgentWorkflowImpl.java](../5-agent-workflow/java/src/main/java/bitovi/AgentWorkflowImpl.java) - Stubbed implementation ⚠️
- [Activities.java](../5-agent-workflow/java/src/main/java/bitovi/activities/Activities.java) - Activities interface (partial, needs updates) ⚠️
- [ActivitiesImpl.java](../5-agent-workflow/java/src/main/java/bitovi/activities/ActivitiesImpl.java) - Stubbed activity implementations ⚠️
- Activity DTOs: `ThoughtResponse`, `ActionResponse`, `ObservationResponse`, `CompactResponse` ✅
- Worker and client already configured ✅

### What Needs Implementation
1. Update workflow interface to accept input and support signals
2. Implement signal handlers for message and exit signals
3. Implement main workflow loop with condition waits
4. Add continue-as-new logic with state preservation
5. Update activities interface to match TypeScript activities
6. Stub out activity implementations with proper signatures

## Implementation Plan

### Step 1: Update Workflow Interface and Input DTO

**Goal:** Define the workflow interface to accept input and support long-running execution

**Actions:**
- Update [AgentWorkflow.java](../5-agent-workflow/java/src/main/java/bitovi/AgentWorkflow.java):
  - Change method signature from `String execute()` to `WorkflowResult execute(WorkflowInput input)`
  - Add `@SignalMethod(name = "agentWorkflowMessage")` for `receiveMessage(MessagePayload payload)` - Must match TypeScript signal name for interoperability
  - Add `@SignalMethod(name = "agentWorkflowExit")` for `requestExit()` - Must match TypeScript signal name for interoperability
- Create DTOs as Java records in separate files in the same package:
  - `WorkflowInput` record with `ContinueAsNewState continueAsNew` (nullable - use plain nullable references for simplicity)
  - `ContinueAsNewState` record with: `List<String> context`, `List<UsageMetadata> usage`, `List<MessagePayload> pending`
  - `MessagePayload` record with: `String name`, `String message`, `String date`
  - `UsageMetadata` record with: `int inputTokens`, `int outputTokens`, `int totalTokens` (use primitives to avoid nulls)
  - `WorkflowResult` record with: `UsageMetadata usage`
- Note: Use plain nullable references (no `@Nullable` annotations or `Optional<T>`) for simplicity in workshop code

**How to verify:**
- Code compiles without errors
- Interface follows Temporal Java SDK conventions for workflow methods and signals
- Matches the TypeScript interface structure: `AgentWorkflowInput` and `AgentWorkflowMessagePayload`

### Step 2: Update Activities Interface

**Goal:** Align activities interface with TypeScript reference implementation

**Actions:**
- Update [Activities.java](../5-agent-workflow/java/src/main/java/bitovi/activities/Activities.java):
  - Remove or comment out old activity signatures (`thought`, `action`, `observation`, `compact`)
  - Add new activity methods matching TypeScript:
    - `ThoughtResponse thoughtEntity(List<String> context)`
    - `String actionEntity(String toolName, Object input)`
    - `ObservationResponse observationEntity(List<String> context, String actionResult)`
    - `CompactResponse compactEntity(List<String> context)`
    - `void persistEntity(List<PersistMessage> messages)`
- Create `PersistMessage` as a single record class in a separate file with nullable fields following Java best practices:
  - `String role` ("user" or "assistant")
  - `String message`
  - `String date` (nullable, only for user messages)
  - `String name` (nullable, only for user messages)
- Update `ThoughtResponse` DTO (separate file) to replace existing structure:
  - Add `String type` field with values "action" or "answer" (use Java convention `type` instead of TypeScript's `__type`)
  - Use nullable fields to handle both types: `String thought`, `String answer` (nullable, for "answer" type), `ActionDetail action` (nullable, for "action" type), `UsageMetadata usage`
  - Rename existing `Action.java` to `ActionDetail.java` and update: change `Object inputs` (plural) to `Object input` (singular) - signature should be: `String name`, `String reason`, `Object input`
- Implement `ObservationResponse` DTO (currently empty):
  - `String observations`
  - `UsageMetadata usage`
- Implement `CompactResponse` DTO (currently empty):
  - `List<String> context` (should return compacted context plus latest 3 entries)
  - `UsageMetadata usage`

**How to verify:**
- Activities interface compiles
- DTOs match the TypeScript type structure
- Activity signatures align with the TypeScript `activities.ts` exports

### Step 3: Stub Activity Implementations

**Goal:** Provide minimal stubbed implementations that can be called without errors

**Actions:**
- Verify Jackson dependency is present in [pom.xml](../5-agent-workflow/java/pom.xml) - if not, add:
  ```xml
  <dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
  </dependency>
  ```
- Update [ActivitiesImpl.java](../5-agent-workflow/java/src/main/java/bitovi/activities/ActivitiesImpl.java):
  - Implement `thoughtEntity()` to return realistic stubbed `ThoughtResponse` with type="answer" and placeholder text like "I'm thinking about your question..."
  - Implement `actionEntity()` to return a simple JSON string like `{"status": "success", "data": "mock result"}` (use Jackson for JSON serialization)
  - Implement `observationEntity()` to return realistic observation message
  - Implement `compactEntity()` to return the original context (no actual compaction)
  - Implement `persistEntity()` to log messages to console with `System.out.println()`
- Each stub should include:
  - Log statement using `System.out.println()` indicating which activity was called
  - Basic error handling with try-catch blocks and error logging
  - Return valid, realistic data structures matching the expected types
  - No null values that would cause NullPointerExceptions
- Use Jackson ObjectMapper for JSON serialization of action inputs and results

**How to verify:**
- All activity methods compile and can be called
- No `UnsupportedOperationException` thrown
- Activities log when invoked
- Return types match interface signatures

### Step 4: Implement Workflow Signal Handlers

**Goal:** Set up signal handlers to receive messages and exit requests

**Actions:**
- In [AgentWorkflowImpl.java](../5-agent-workflow/java/src/main/java/bitovi/AgentWorkflowImpl.java):
  - Add instance variables:
    - `List<MessagePayload> pending = new ArrayList<>()`
    - `boolean userRequestedExit = false`
  - Implement `receiveMessage(MessagePayload payload)` method:
    - Add payload to `pending` list
    - Log receipt of message
    - Note: Messages are processed sequentially, no need for concurrent signal handling
  - Implement `requestExit()` method:
    - Set `userRequestedExit = true`
    - Log exit request

**How to verify:**
- Workflow compiles with signal methods
- Signal methods modify workflow instance state
- No errors when signals are received (will test in later steps)

### Step 5: Implement Main Workflow Loop Structure

**Goal:** Create the core event loop that processes messages

**Actions:**
- In the `execute(WorkflowInput input)` method:
  - Initialize state from input:
    - `List<String> context = input.continueAsNew != null ? input.continueAsNew.context : new ArrayList<>()`
    - `List<UsageMetadata> usage = input.continueAsNew != null ? input.continueAsNew.usage : new ArrayList<>()`
    - `pending` list already initialized in Step 4
  - Wait for first message: `Workflow.await(() -> !pending.isEmpty() || userRequestedExit)`
  - Implement main loop: `while (true)` - Note: Loop exits via `return` statements (when `userRequestedExit` is true) or `continueAsNew()` (when history grows large)
    - Check if exit requested → aggregate usage and return
    - Process all pending messages
    - Call `thoughtEntity` activity
    - Handle "answer" type response
    - Handle "action" type response (call `actionEntity` and `observationEntity`)
    - Check for continue-as-new suggestion
- Use `Workflow.await()` for condition-based waiting (equivalent to TypeScript `condition()`)
- Follow the same control flow as the TypeScript implementation

**How to verify:**
- Workflow compiles
- Control flow matches TypeScript reference
- Each await/condition is properly placed
- Loop can handle both message types (action and answer)

### Step 6: Implement Continue-As-New Logic

**Goal:** Support long-running conversations with workflow history management

**Actions:**
- After processing an action and observation:
  - Check `Workflow.getInfo().isContinueAsNewSuggested()`
  - If true:
    - Call `compactEntity` activity to compress context
    - Wait for all signal handlers to finish: `Workflow.await(() -> Workflow.allHandlersFinished())`
    - Create new `ContinueAsNewState` with current state:
      - `context` (compacted)
      - `usage` (accumulated metrics)
      - `pending` (any unprocessed messages)
    - Call `Workflow.continueAsNew(new WorkflowInput(continueAsNewState))`
  - Use Java's `Workflow.continueAsNew()` method

**How to verify:**
- Continue-as-new logic compiles
- State preservation includes context, usage, and pending messages
- Logic matches TypeScript `continueAsNew<typeof agentWorkflow>()` call

### Step 7: Implement Usage Tracking

**Goal:** Accumulate token usage metrics across activities and continue-as-new boundaries

**Actions:**
- When `thoughtEntity` returns with usage metadata:
  - Check if `usage` field is non-null
  - Add to `usage` list: `usage.add(thoughtResponse.usage)`
- When `observationEntity` returns with usage metadata:
  - Check if `usage` field is non-null
  - Add to `usage` list: `usage.add(observationResponse.usage)`
- When `compactEntity` returns with usage metadata:
  - Add to `usage` list before continue-as-new
- On exit (when `userRequestedExit` is true):
  - Aggregate all usage metrics into a single `UsageMetadata`:
    ```java
    UsageMetadata finalUsage = usage.stream()
        .reduce(new UsageMetadata(0, 0, 0),
            (acc, curr) -> new UsageMetadata(
                acc.inputTokens + curr.inputTokens,
                acc.outputTokens + curr.outputTokens,
                acc.totalTokens + curr.totalTokens
            ));
    ```
  - Return `new WorkflowResult(finalUsage)`

**How to verify:**
- Usage is accumulated from all activities that return it
- Aggregation logic matches TypeScript `reduce()` operation
- Final usage is returned on exit

### Step 8: Implement Context Building

**Goal:** Build properly formatted context entries for the AI agent

**Actions:**
- When processing pending messages:
  - Create user message entries: `<user_message name="${name}" date="${date}">\n${message}\n</user_message>`
  - Add to context list
  - Before adding to context, call `persistEntity` with the pending messages
  - Clear message from pending after processing
- After receiving thought response:
  - For "answer" type: add `<answer>\n${answer}\n</answer>` to context
  - For "action" type: 
    - Add `<thought>\n${thought}\n</thought>`
    - Add `<action><reason>\n${reason}\n</reason><name>${name}</name><input>${JSON}</input></action>`
    - Use Jackson ObjectMapper to serialize action input to JSON string
- After receiving observation:
  - Add `<observation>\n${observations}\n</observation>` to context
- Use String.format() or StringBuilder for XML-like formatting
- Match the exact format from TypeScript (XML-like tags with newlines)

**How to verify:**
- Context entries match TypeScript formatting
- Context grows as workflow processes messages
- XML-like structure is preserved (important for prompt engineering)

### Step 9: Handle Wait Conditions Properly

**Goal:** Ensure workflow waits at appropriate points without busy-waiting

**Actions:**
- After answering user (answer type response):
  - Wait for next message or exit: `Workflow.await(() -> !pending.isEmpty() || userRequestedExit)`
- After processing action (before next thought):
  - No wait needed - continue immediately to next iteration
- Use `Workflow.await()` with lambda predicates
- Never use `Thread.sleep()` or blocking operations
- All conditions should check signal-modified state

**How to verify:**
- Workflow uses `Workflow.await()` for all condition checks
- No busy loops or sleep calls
- Matches TypeScript `await condition(() => ...)` calls

### Step 10: Update Client for Basic Workflow Testing

**Goal:** Verify basic workflow execution with message → answer flow

**Actions:**
- Update [AgentWorkflowClient.java](../5-agent-workflow/java/src/main/java/bitovi/AgentWorkflowClient.java):
  - Create typed workflow stub for testing
  - Start workflow asynchronously with empty input: `WorkflowClient.start(workflow::execute, new WorkflowInput(null))`
  - After starting, send test message signal:
    ```java
    workflow.receiveMessage(new MessagePayload(
        "TestUser",
        "Hello, agent!",
        LocalDateTime.now().toString()
    ));
    ```
  - Wait briefly (use `Thread.sleep(2000)` in client, not workflow)
  - Send exit signal: `workflow.requestExit()`
  - Get result: `WorkflowResult result = WorkflowStub.fromTyped(workflow).getResult(WorkflowResult.class)`
  - Print usage metrics
- Note: This is just for testing - real usage will be via the agent-chat-server

**How to verify:**
- Client can start workflow
- Workflow receives signals
- Workflow processes messages and exits gracefully
- Usage metrics are returned

### Step 11: End-to-End Testing

**Goal:** Verify complete workflow execution with stubbed activities

**Actions:**
- Use VS Code launch configuration "Exercise 5 - Worker"
- Start worker and verify it registers successfully
- Use VS Code launch configuration "Exercise 5 - Client" (modified in Step 10)
- Run client and observe:
  - Workflow starts
  - Message signal is received
  - Activities are invoked (check activity stub logs)
  - Exit signal is received
  - Workflow completes with usage metrics
- Check Temporal Web UI at http://localhost:8233:
  - Workflow execution appears
  - Signals are recorded in event history
  - Activity executions are visible
  - Workflow completes successfully

**How to verify:**
- Worker starts without errors
- Client completes workflow execution
- Temporal UI shows complete workflow history
- All events (signals, activities) are recorded
- Workflow returns valid result
- No exceptions or errors in console output

### Step 12: Verify Continue-As-New (Optional/Advanced Testing)

**Goal:** Test that continue-as-new works when workflow history grows (optional step for advanced verification)

**Actions:**
- Manual testing via Temporal UI is sufficient
- Temporarily modify the workflow to suggest continue-as-new after first action:
  - Add a counter or force `Workflow.getInfo().isContinueAsNewSuggested()` to return true
- Send multiple messages to trigger continue-as-new
- Verify in Temporal UI:
  - Original workflow shows "Continued As New" status
  - New workflow execution is created with same workflow ID
  - State (context, usage, pending) is preserved across boundary

**How to verify:**
- Continue-as-new creates new workflow execution
- State is preserved in the new execution
- Workflow can continue processing after continue-as-new
- Usage metrics are accumulated across boundaries

## Notes

- The workflow implementation should focus on structure and control flow, not AI/activity logic
- Activity implementations will be enhanced in subsequent tasks
- Follow Java conventions:
  - Use Java records for DTOs (Java 16+ is available)
  - Create separate files for each DTO class
  - Use primitive types for usage metrics to avoid nulls where possible
  - Use nullable fields to handle variant types (ThoughtResponse, PersistMessage)
  - Use Jackson ObjectMapper for JSON serialization
  - Proper null handling with nullable fields where needed
  - Java streams for aggregation
- Use Temporal Java SDK patterns: `Workflow.await()`, `Workflow.continueAsNew()`, `@SignalMethod`
- Signal names: In Temporal Java SDK, signal names default to the method name unless overridden with `@SignalMethod(name = "...")`
- Preserve exact XML-like context formatting from TypeScript (critical for AI prompts)
- Testing with stubbed activities validates the workflow orchestration logic
- The agent-chat-server (from 580-2) will be the primary client for this workflow
- Messages are processed sequentially - no concurrent signal handling needed

## Summary

All questions have been answered and incorporated into the specification above. The implementation should now:
- Use explicit signal names matching TypeScript for interoperability
- Use plain nullable references for simplicity
- Follow Java naming conventions (e.g., `type` instead of `__type`)
- Include all necessary DTOs including `UsageMetadata`
- Rename `Action.java` to `ActionDetail.java` with updated signature
- Verify and add Jackson dependency if needed
- Use simple but valid JSON for stubbed responses
- Include error handling and logging in all stubs
- Separate basic (Step 10) and advanced (Step 12) testing
