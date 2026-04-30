# Exercise 5 - Agent Workflow

## Goals

Build a ReAct (Reasoning and Acting) agent using Temporal Workflows, combining the building blocks from previous exercises -- prompt engineering, RAG, tool calling, and MCP -- into a durable, stateful agent loop.

By the end of this exercise, you should understand:

- How the ReAct pattern maps to Temporal Workflows and Activities
- How Signals let external systems communicate with a running agent
- How context is maintained and compacted across long-running interactions
- How Temporal provides durability, retries, and visibility for agent workloads
- How orchestration and LLM responsibilities are separated
- Patterns that extend this foundation for production use

## What you need to know

### Why Temporal for AI Agents?

LLM agents are long-running and non-deterministic: a single query may require multiple reasoning rounds, external tool calls, and human interaction spread over minutes or hours. Request-response architectures lack built-in mechanisms for cross-step state, retries, and surviving process restarts. Temporal models the entire agent loop as a durable Workflow, giving us:

**Durability.** If the worker crashes mid-execution, Temporal replays the workflow from its event history and resumes exactly where it left off -- critical for agents running hours or days.

**Automatic Retries.** Failed Activities (LLM timeouts, tool errors, rate limits) are retried per a configurable policy (here: 3 attempts, 120s timeout). This is a safety net for non-deterministic LLM output -- if the thought activity fails to parse a response, the retry usually produces valid output.

**Signals for Human-in-the-Loop.** Signals deliver messages into a running workflow without interrupting it -- the foundation for agents that pause, wait for input, and resume without losing state.

**Visibility.** Every Activity, Signal, and state transition is recorded. Inspect any workflow in the [Temporal UI](http://localhost:8233) to see what the agent did and what the LLM returned at each step.

**Infinite Duration via Continue-As-New.** Temporal caps event history size. `continueAsNew` restarts the workflow with a fresh history while preserving forwarded state -- letting the agent compact its context and run indefinitely.

### The ReAct Pattern

ReAct (Reasoning and Acting) is an agent architecture where the LLM alternates between reasoning about a problem and taking actions to gather information or perform tasks. The core loop looks like this:

```
IDLE -- waiting for a user message
  |
  v
THINKING -- the LLM analyzes the query and all previous context,
            then decides: provide a final answer, or call a tool?
  |
  +-- answer --> add to context, return to IDLE
  |
  +-- action --> move to ACTING
                   |
                   v
                 ACTING -- execute the chosen tool
                   |
                   v
                 OBSERVING -- the LLM summarizes the tool's output
                   |
                   v
                 back to THINKING (loop continues)
```

Each iteration appends to the context (thoughts, actions, observations), so the LLM can reference its own reasoning history in subsequent steps. Unlike a single prompt-response call, ReAct lets the LLM iteratively refine its understanding by interacting with external tools.

### Division of Responsibilities

The Workflow and LLM layers have clearly separated jobs:

- **Temporal owns orchestration** -- _what_ happens next. It drives state-machine transitions, enforces ordering, handles retries, and ensures durability. The workflow never calls the LLM directly; it delegates to Activities and branches on their structured responses.
- **The LLM owns reasoning** -- _how_ to answer. Given accumulated context, it chooses whether to call a tool or provide an answer, generates parameters, and summarizes observations. It has no knowledge of Temporal.

The Activities interface is the boundary. You can swap LLM providers or prompt strategies without touching orchestration, and migrate orchestration frameworks without rewriting LLM logic.

### How it works

The implementation has two layers: the Workflow (orchestration) and the Activities (the actual work).

#### Workflow Interface

The `AgentWorkflow` interface defines one main method and three Signals:

```java
@WorkflowInterface()
public interface AgentWorkflow {
    @WorkflowMethod(name = "agentWorkflow")
    WorkflowResult execute(WorkflowInput input);

    @SignalMethod(name = "message")
    void receiveMessage(MessagePayload payload);

    @SignalMethod(name = "exit")
    void requestExit();

    @SignalMethod(name = "continueAsNew")
    void requestContinueAsNew();
}
```

- `execute` -- entry point. Runs the main event loop until exit. Accepts `WorkflowInput`, which optionally carries state forwarded from a previous run (for continue-as-new).
- `receiveMessage` -- delivers user messages (name, text, timestamp) into the running workflow.
- `requestExit` -- shuts down gracefully and returns aggregated token usage in the `WorkflowResult`.
- `requestContinueAsNew` -- triggers context compaction and restarts via `continueAsNew`.

#### Workflow State

The workflow maintains several pieces of state that persist across the entire lifecycle of the agent:

```java
// Signal state -- populated by incoming Signals
private List<MessagePayload> pendingMsgs = new ArrayList<>();
private boolean userRequestedExit = false;
private boolean userRequestedCompaction = false;

// ReAct loop state
private ReactStep reactStep = ReactStep.IDLE;
```

`ReactStep` is an enum: `IDLE`, `THINKING`, `ACTING`, `OBSERVING`. Two local variables in `execute` round out the state: `context` (the XML-formatted reasoning history) and `usage` (accumulated token counts).

#### The Main Event Loop

The `execute` method runs:

```
1. Initialize context and usage from input (or empty if first run)
2. Wait for a message, exit request, or compaction request
3. Loop:
   a. If exit requested --> aggregate usage, return result
   b. If compaction needed --> compact context, continueAsNew
   c. If messages pending and IDLE --> drain messages into context, set THINKING
   d. If THINKING --> call thoughtActivity
      - If answer --> add to context, persist, set IDLE
      - If action --> add thought + action to context, set ACTING
        - Call actionActivity, set OBSERVING
        - Call observationActivity, add observation to context
        - Set THINKING, continue loop
   e. Wait for next message, exit, or compaction request
```

Durability matters most at the `Workflow.await()` calls: if the worker dies while idle, Temporal replays and lands back at the same await, ready for the next Signal.

#### Context Formatting

Context entries use XML-like tags so the LLM can distinguish information types:

```xml
<user_message name="Mark" date="2025-01-15">What is the weather in Austin?</user_message>
<thought>I need to check the weather. I'll use the brave_search tool.</thought>
<action><reason>Search for current weather</reason><name>brave_search</name><input>{"q":"weather Austin TX"}</input></action>
<observation>Current weather in Austin is 72F and sunny.</observation>
<answer>The current weather in Austin, TX is 72 degrees and sunny.</answer>
```

#### Activities Interface

Activities are the units of work. They run outside the workflow's deterministic execution, so they can make network calls, hit databases, and interact with external APIs.

```java
@ActivityInterface
public interface Activities {
    ThoughtResponse thoughtActivity(List<String> context);
    String actionActivity(String toolName, ActionInput input);
    ObservationResponse observationActivity(List<String> context, String actionResult);
    CompactResponse compactActivity(List<String> context);
    void persistActivity(List<PersistMessage> messages);
}
```

Each Activity uses a 120-second timeout and up to 3 retries:

```java
private final ActivityOptions defaultActivityOptions = ActivityOptions
    .newBuilder()
    .setStartToCloseTimeout(Duration.ofSeconds(120))
    .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
    .build();
```

#### Thought Activity

The core decision-making step. It sends the full context to the LLM with a system prompt instructing it to either provide a final answer or choose a tool. The prompt includes the available tools as XML (`ToolRegistry.getToolsAsXmlString()`), the current date (LLMs have a training cutoff), and all prior ReAct steps.

The LLM responds with one of two JSON shapes:

Tool call:

```json
{
  "thought": "I need to search for the current weather in Austin",
  "action": {
    "name": "brave_search",
    "reason": "Search for current weather data",
    "input": { "q": "weather Austin TX" }
  }
}
```

Final answer:

```json
{
  "thought": "I now have the weather data from my search",
  "answer": "The current weather in Austin, TX is 72 degrees and sunny."
}
```

Uses the high-quality model (`AWS_MODEL_ID`) because reasoning quality here drives whether the agent picks the right tool and produces useful answers.

#### Action Activity

Looks up a tool in the `ToolRegistry`, validates it, passes parameters, and returns the raw result. If the tool is not found, returns a JSON error rather than throwing -- the LLM sees the error in the next observation step and can recover by choosing a different approach.

Registered tools:

- `brave_search` -- Brave Search API. Accepts `q` and an optional result count.
- `fetch_webpage` -- HTTP GET on a `url`, returns raw HTML.

`ToolRegistry` maps tool names to executors. Each tool provides a Bedrock-compatible `ToolSpecification` (JSON Schema input) and an `execute` method. Adding a tool means implementing both and registering it.

#### Observation Activity

Summarizes raw tool output into a concise observation, extracting relevant info and formatting it for the next thinking step. Uses the cheaper model (`AWS_LOW_MODEL_ID`) -- a cost optimization since summarization needs less reasoning than the thought step.

#### Compact Activity

Compresses the context history. Triggered manually via the `compact` Signal or automatically when Temporal suggests continue-as-new:

1. Send the full context to the LLM with summarization instructions.
2. The LLM returns a compressed summary.
3. Build a new context: summary + the last 3 entries (preserving recent state).
4. Call `continueAsNew` with the compacted context, accumulated usage, and any pending messages.

This is what lets the agent run indefinitely.

#### Persist Activity

Records user and assistant messages to an external store. The workshop logs to the console; extend with a database or message queue for production. Called when user messages arrive and when the agent produces an answer.

### Token Usage Tracking

Each LLM-calling Activity returns `UsageMetadata` (`inputTokens`, `outputTokens`, `totalTokens`). The workflow accumulates these and returns the aggregate on exit -- giving visibility into conversation cost for budgeting and optimization.

### Taking It Further

Patterns that extend the base architecture for production use.

#### Signal With Start

The workshop starts the workflow and sends the first message as separate operations, which creates a race -- and requires you to know whether the workflow is already running. `signalWithStart` atomically starts a workflow (if not already running) and delivers a Signal in one call. This is the standard pattern when each user conversation maps to a long-running workflow:

```typescript
// TypeScript example using signalWithStart
await client.workflow.signalWithStart(agentWorkflow, {
  taskQueue: "agent-task-queue",
  workflowId: workflowId,
  args: [{}],
  signal: agentWorkflowMessageSignal,
  signalArgs: [message, author, new Date().toISOString()],
});
```

In Java, use `SignalWithStartBatchRequest` or `newSignalWithStartRequest` on the workflow client. Either way, you never need to pre-check whether the workflow is running.

#### Per-Activity Timeout Configuration

The workshop uses one timeout for all Activities (120s, 3 retries). In practice, Activities have different performance profiles:

```typescript
// Fast, lightweight operations -- short timeouts
const { persistUserMessage, persistAgentMessage } = proxyActivities({
  startToCloseTimeout: "15 seconds",
  retry: { maximumAttempts: 5 },
});

// The thought activity calls a high-quality LLM that may take time to reason
const { thought, action } = proxyActivities({
  startToCloseTimeout: "5 minutes",
  retry: { maximumAttempts: 5 },
});

// Summarization and compaction use a faster model
const { compact, observation } = proxyActivities({
  startToCloseTimeout: "1 minute",
  retry: { maximumAttempts: 5 },
});
```

The thought activity needs longer because complex reasoning can take 30-60+ seconds. Persist should be milliseconds -- 15s is generous and a failure signals a real problem. Observation and compact are in between (cheaper, faster model).

In Java, create multiple `ActivityOptions` objects and use them with different `ActivityStub` instances per Activity type.

#### Error Handling with Context Recovery

The workshop relies on Temporal's standard retries. A more resilient pattern catches Activity failures in the workflow loop and pushes the error into the agent's context, letting the LLM recover by trying a different approach:

```typescript
while (true) {
  try {
    // ... normal ReAct loop ...
    const actionResult = await action(toolName, toolInput);
    const agentObservation = await observation({ context, actionResult });
    context.push(`<observation>\n${agentObservation}\n</observation>`);
  } catch (error: unknown) {
    if (error instanceof ActivityFailure) {
      context.push(
        `<error>\nActivityFailure: ${error.cause?.message}\n</error>`,
      );
    } else {
      context.push(
        `<error>\nUnknown Error: ${(error as Error).message}\n</error>`,
      );
    }
  }
}
```

If `brave_search` is down, the LLM sees `<error>ActivityFailure: Brave Search API returned 503</error>` and may try `fetch_webpage` or tell the user the service is unavailable -- graceful degradation instead of crashing.

#### Token-Based Proactive Compaction

The workshop compacts manually or when Temporal suggests `continueAsNew` (based on event-history size). A proactive approach also counts tokens and compacts before the LLM's context window fills:

```typescript
if (agentThought.__type === "string") {
  context.push(`<answer>\n${agentThought.payload}\n</answer>`);

  // Count tokens in the current context
  const tokenCount = await tokens(context);
  const passedTokenLimit = tokenCount.tokenCount > tokenCount.tokenLimit;

  // Compact if either Temporal suggests it or we are approaching the token limit
  if (workflowInfo().continueAsNewSuggested || passedTokenLimit) {
    return compactAndContinueAsNew();
  }

  await continueCondition();
}
```

`continueAsNewSuggested` reflects event-history size (a Temporal concern); the LLM has a separate concern -- its context window. A conversation may not trip Temporal's suggestion while still approaching the token limit. Counting tokens proactively (a lightweight Activity using `tiktoken` or your provider's tokenizer) prevents context overflow.

#### Query Handlers for State Inspection

Temporal Queries read a running workflow's state without affecting execution -- useful for UIs showing conversation history, debugging stuck agents, or monitoring context size:

```typescript
// Define a Query handler in the workflow
export const agentWorkflowQueryContext = defineQuery<string[]>(
  "agentWorkflowQueryContext",
);

// Inside the workflow function
setHandler(agentWorkflowQueryContext, () => {
  return context;
});
```

In Java, use `@QueryMethod` on the workflow interface. Unlike Signals, Queries are synchronous -- the caller gets an immediate response.

#### Additional Signal Types

A production agent might support more Signals beyond message/exit/compact:

**External context injection** lets non-user systems push information into the agent's context -- e.g., a monitoring system signaling an alert or a scheduled job injecting daily data:

```xml
<external_context date="2025-01-15" author="system">
Server CPU usage exceeded 90% for the last 15 minutes on host prod-web-03.
</external_context>
```

**External artifact signals** attach file references or structured data without embedding the full content. The agent sees a description and a reference ID, and can fetch the artifact via tools if needed:

```xml
<external_artifact date="2025-01-15" mimetype="application/pdf" id="doc-12345">
<description>
Q4 2024 Financial Report - 47 pages, includes revenue breakdown by region.
</description>
</external_artifact>
```

**Context clearing** resets the conversation history without stopping the workflow -- useful for multi-tenant systems where one workflow instance serves different sessions over time.

All of these follow the same principle: Signals are the interface through which the outside world communicates with the agent, and the workflow incorporates them into the context the LLM reasons about.
