# SYSTEMS-580-6: Trigger Compaction

## Overview

Add the ability to manually trigger context compaction and continue-as-new behavior in the agent workflow. This allows users to explicitly compact the conversation history when needed, rather than waiting for Temporal's automatic continue-as-new suggestion.

**Key Components:**
- New signal method in [AgentWorkflow.java](../5-agent-workflow/java/src/main/java/bitovi/AgentWorkflow.java) interface
- Signal handler implementation in [AgentWorkflowImpl.java](../5-agent-workflow/java/src/main/java/bitovi/AgentWorkflowImpl.java)
- UI button in [index.html](../agent-chat-server/public/index.html)
- Server endpoint in [server.ts](../agent-chat-server/src/server.ts)

**Reference Implementation:**
- The TypeScript reference code at [temp-ref-code/src/workflows/workflow.ts](../temp-ref-code/src/workflows/workflow.ts) only supports automatic compaction via `continueAsNewSuggested`
- This feature is a new enhancement not present in the reference implementation

## Implementation Plan

### Step 1: Add Signal Method to Workflow Interface

**Goal:** Define the new signal method for triggering compaction

**Actions:**
- In [AgentWorkflow.java](../5-agent-workflow/java/src/main/java/bitovi/AgentWorkflow.java):
  - Add a new `@SignalMethod` annotation with name `"agentWorkflowCompact"`
  - Declare method signature: `void requestCompaction()`
  - Place it after the existing `requestExit()` signal method

**How to verify:**
- Interface compiles successfully
- Signal method follows same pattern as existing `receiveMessage()` and `requestExit()` methods
- Method name clearly indicates its purpose

### Step 2: Implement Signal Handler in Workflow

**Goal:** Add state tracking and handler for compaction requests

**Actions:**
- In [AgentWorkflowImpl.java](../5-agent-workflow/java/src/main/java/bitovi/AgentWorkflowImpl.java):
  - Add instance variable after `userRequestedExit`:
    - `private boolean userRequestedCompaction = false;`
  - Implement `requestCompaction()` method after `requestExit()`:
    - Set `userRequestedCompaction = true`
    - Log compaction request using `Workflow.getLogger()`
    - Example: `Workflow.getLogger(AgentWorkflowImpl.class).info("Compaction requested")`

**How to verify:**
- Implementation compiles
- Signal handler follows same pattern as `requestExit()`
- State variable is properly initialized

### Step 3: Add Compaction Trigger Logic and Safety Checks to Workflow Loop

**Goal:** Modify the existing continue-as-new condition to check for manual compaction request, and add safety logic to prevent race conditions

**Actions:**
- In [AgentWorkflowImpl.java](../5-agent-workflow/java/src/main/java/bitovi/AgentWorkflowImpl.java):
  - **IMPORTANT:** A ReAct loop is considered "complete" when a thought with type "answer" is received (not when type is "action"). The compaction check must be MOVED to this location.
  - **REMOVE** the existing continue-as-new check that is currently after observation is added to context (around line 165)
  - **ADD** the continue-as-new check after the "answer" type thought is processed (around line 114, after `activities.persistActivity(assistantMessages)` and before the await for next message)
  - The continue-as-new check should be:
    - `if (Workflow.getInfo().isContinueAsNewSuggested() || userRequestedCompaction)`
  - This triggers compaction when EITHER:
    - Temporal suggests continue-as-new (automatic)
    - User explicitly requests compaction (manual)
  - Add NEW safety logic inside the conditional block to prevent signal race conditions:
    - **ADD:** `Workflow.await(() -> Workflow.allHandlersFinished());` (BEFORE compactActivity call)
    - This ensures no signals are being processed during continue-as-new
    - Prevents lost messages if signals arrive during compaction
  - Then execute compaction logic:
    - Compacts context via `activities.compactActivity(context)`
    - If compactActivity fails, allow the exception to propagate (this will terminate the workflow)
    - Tracks usage from compaction response
    - Creates `ContinueAsNewState` with compacted context, usage, and pending messages
    - Calls `Workflow.continueAsNew(new WorkflowInput(continueAsNewState))`

**How to verify:**
- Single unified condition handles both automatic and manual compaction
- Compaction logic is MOVED from after observation to after "answer" thought (no duplication)
- NEW `allHandlersFinished()` wait is added before compaction to prevent race conditions
- Compaction check only occurs after ReAct loop completion (when "answer" type thought is received)
- Compaction does NOT occur after observation in the "action" path (since loop is not complete)
- All required state (context, usage, pending) is preserved
- Flag `userRequestedCompaction` is automatically reset in new workflow execution (because continueAsNew creates a completely new workflow instance, re-initializing all instance variables to their default values)
- If compactActivity fails, the workflow terminates rather than continuing with uncompacted context

### Step 4: Add Server Endpoint for Compaction Signal

**Goal:** Create HTTP endpoint to send compaction signal to workflow

**Actions:**
- In [server.ts](../agent-chat-server/src/server.ts):
  - Add constant at top (after EXIT_SIGNAL): `const COMPACT_SIGNAL = 'agentWorkflowCompact';`
  - Add new POST endpoint after `/api/conversations/:id/exit`:
    - Route: `/api/conversations/:id/compact`
    - Extract `id` from params
    - Get workflow handle from `workflowSessions.get(id)`
    - Return 404 if handle not found
    - Send signal: `await handle.signal(COMPACT_SIGNAL)`
    - Return success response: `res.json({ success: true })`
    - Wrap in try-catch and return 500 on error

**How to verify:**
- Endpoint follows same pattern as message and exit endpoints
- Proper error handling for missing conversation
- Returns appropriate HTTP status codes
- Signal name matches the one defined in Java interface

### Step 5: Add UI Button and Handler

**Goal:** Provide user interface control for triggering compaction

**Actions:**
- In [index.html](../agent-chat-server/public/index.html):
  - Locate the message panel div (around line 38) with Send and Exit buttons
  - Modify the button layout to display three buttons in this order: **[Send] [Compact] [Exit]**
    - Change Send button width from `48%` to `32%`
    - Change Exit button width from `48%` to `32%`
    - Add new Compact button between Send and Exit:
      - Text: "Compact"
      - Width: `32%`
      - Margin: `0 1%`
      - onclick: `compactConversation()`
      - Style: Default blue button (not danger class)
  - Add JavaScript function at the end of script section (before updateConversationSelect):
    ```javascript
    async function compactConversation() {
      if (!currentConversationId) return;
      
      const res = await fetch(`/api/conversations/${currentConversationId}/compact`, { method: 'POST' });
      const data = await res.json();
      
      if (data.success) {
        document.getElementById('events').innerHTML += '<div class="event" style="background: #e8f4f8;">Context compacted and workflow restarted</div>';
      }
    }
    ```

**Design Decisions:**
- Button is always available as long as a conversation is selected (no validation for minimum context size)
- No confirmation dialog before triggering compaction
- Button remains enabled during workflow operations

**How to verify:**
- Three buttons display evenly spaced in message panel
- Compact button has same styling as Send button
- Button is disabled when no conversation is selected
- Success message appears in events panel after compaction
- Function follows same pattern as `exitConversation()`

### Step 6: Test End-to-End Flow

**Goal:** Verify the complete compaction feature works correctly

**Actions:**
- Run the "Docker Compose Up" task in VS Code (starts agent-chat-server, Temporal, and other dependencies)
- Start the Exercise 5 Worker
- Open http://localhost:3000 in browser
- Create a new conversation
- Send several messages to build up context
- Click the "Compact" button
- Verify in Temporal UI:
  - Workflow shows continue-as-new event
  - New workflow execution is created with same workflow ID
  - Context is compacted (check workflow history)
- Send another message after compaction
- Verify conversation continues normally with compacted context

**How to verify:**
- No errors in server logs or browser console
- Compaction event appears in UI event stream
- Workflow continues to accept messages after compaction
- Context size is reduced after compaction (visible in workflow history)
- Usage metrics are preserved across continue-as-new
- Pending messages are preserved if compaction happens while messages are queued

## Technical Notes

### Continue-As-New Behavior
- `Workflow.continueAsNew()` terminates the current workflow execution and starts a new one
- All state must be explicitly passed in the `ContinueAsNewState`
- Signal handlers are automatically finished before continue-as-new executes
- The new workflow execution gets a new event history but keeps the same workflow ID

### Compaction Strategy
- The `compactActivity` uses an LLM to summarize conversation history
- It keeps the compacted summary plus the last 3 context entries
- This reduces context size while preserving recent conversation details
- The prompt template is at `src/main/resources/prompts/compact-prompt.txt` (classpath resource)

### Signal Processing
- Temporal processes signals sequentially in workflow execution
- `Workflow.allHandlersFinished()` ensures no signals are being processed during continue-as-new
- The `userRequestedCompaction` flag persists across signal invocations within the same execution
- After continue-as-new, the flag is reset to false in the new execution (instance variables are re-initialized)

### ReAct Loop Protection
- A ReAct loop is considered "complete" when a thought with type "answer" is received (indicating the agent is ready to respond to the user)
- The compaction check is intentionally placed AFTER the "answer" type thought is processed and persisted, NOT after observation
- This is the correct location because:
  - An "answer" thought means the agent has finished reasoning and is providing a final response
  - An "action" thought followed by observation means the loop must continue (more reasoning needed)
- This prevents compaction from interrupting mid-loop state (e.g., between thought→action→observation)
- The workflow structure ensures compaction only happens at safe breakpoints in execution
- If `userRequestedCompaction` is set during a ReAct loop (while processing actions), compaction will trigger when the loop reaches completion ("answer" thought)

### User Experience Decisions
- Compaction button is always available when a conversation is active (no minimum context size validation)
- No confirmation dialog before compaction (allows quick, seamless operation)
- Context size information is not displayed in the UI (kept simple for this iteration)
- Compaction statistics are not emitted or displayed (can be added later if needed)
- Button remains enabled during workflow operations (Temporal handles signal queuing safely)
- Multiple rapid clicks on "Compact" button will queue multiple signals (no special handling to prevent this)
- If compaction fails, the workflow terminates and the conversation ends (error handling can be enhanced in future iterations)

## Notes on Implementation