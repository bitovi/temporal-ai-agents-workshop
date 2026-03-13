# "My Agent Calls Your Agent" — Implementation Spec

## Overview

Replace the current Exercise 8 book-agent scenario with a new scenario that demonstrates real A2A capabilities: a user's **personal assistant agent** contacts a company's **support agent** to resolve a billing dispute. This makes A2A's differentiators tangible — opacity, multi-turn negotiation (`input-required`), agent discovery, artifacts, and task lifecycle — because the two agents are owned by different parties with different trust levels.

The scenario: a user tells their personal assistant "I got charged twice for the Battle Pass in Pixel Warriors. Handle it." The personal assistant discovers Pixel Forge's support agent, opens a task, gets asked for identity verification (multi-turn `input-required`), and receives a refund receipt artifact on completion.

## Current State

The existing Exercise 8 treats the book agent as a tool call wrapped in A2A protocol — no multi-turn negotiation, no `input-required` state, no artifacts, no agent discovery. Indistinguishable from an MCP tool call with extra steps.

### Key Existing Files

| File | Current Role |
|------|-------------|
| `BookAgentTool.java` | A2A client — sends query, receives flat text |
| `ToolRegistry.java` | Hardcodes `book_agent` alongside other tools |
| `AgentToAgentWorkflowImpl.java` | Main agent ReAct loop (no A2A-specific logic) |
| `ActivitiesImpl.java` | Activity stubs for thought/action/observation |
| `book-agent-server/server.ts` | Standalone A2A book agent with Gutendex tools |
| `agent-chat-server/` | Chat UI backend (Express + SSE + Temporal) and frontend |
| `docker-compose.yml` | Service topology |

### SDK Versions

- **Java A2A client:** `io.github.a2asdk:a2a-java-sdk-client` v0.3.3.Final
- **TypeScript A2A server:** `@a2a-js/sdk` v0.3.0
- Both implement A2A protocol v0.3.0

---

## Architecture: Temporal ↔ Remote Agent Interaction

The Java/Temporal personal assistant is the A2A **client**. The TypeScript support agent is the A2A **server** (Express on port 4000, gRPC on port 4001). Every A2A interaction is **client-initiated request → server streams response** — no inbound connections to the workflow.

```
                    outbound HTTP POST (JSON-RPC)
  SupportAgentTool ──────────────────────────────► support-agent-server (Express)
  (Temporal activity)                               (TS, port 4000 HTTP / 4001 gRPC)
                    ◄──────────────────────────────
                    streamed SSE/JSON-RPC events
                    (working, working, input-required)
```

Each A2A round-trip is a **synchronous Temporal activity**: `actionActivity` starts, blocks while the HTTP stream is open, and returns a result string. Streaming `TaskUpdateEvent`s arrive as callbacks on the already-open HTTP response — no webhook needed.

When the support agent returns `input-required`, the activity finishes and returns structured JSON. The workflow's `Workflow.await(() -> !pendingMsgs.isEmpty())` **durably suspends** — no thread, no HTTP connection, no resources held. When the user responds via the chat UI signal, the workflow wakes, the LLM calls `support_agent` again with saved `taskId`/`contextId`, opening a **new** HTTP connection for Round 2.

### End-to-End Walkthrough (Protocol + UI Events)

**Round 1 — initial request:**

| # | What happens | UI event |
|---|---|---|
| 1 | ReAct loop calls `actionActivity("support_agent", { message: "..." })` | `thought`, `action` |
| 2 | `SupportAgentTool` fetches agent card | `a2a_discovery` |
| 3 | Opens outbound HTTP via `client.sendMessage()` | `a2a_task_submitted` |
| 4 | TS server runs ReAct loop, streams `working` events back | `a2a_working` (×N) |
| 5 | TS server emits `input-required` — Java captures `taskId`/`contextId` | `a2a_input_required` |
| 6 | Activity returns `{ "status": "input_required", ... }` | `observation` |
| 7 | LLM generates "The agent needs you to verify your email" | `answer` |

**The gap — Temporal handles the wait:**

8. `Workflow.await(() -> !pendingMsgs.isEmpty())` — durable suspend
9. User types their email in chat UI → `receiveMessage` signal → workflow wakes

**Round 2 — follow-up:**

| # | What happens | UI event |
|---|---|---|
| 10 | LLM calls `support_agent({ message: "mark@example.com", taskId, contextId })` | `thought`, `action` |
| 11 | New activity → new HTTP connection; TS server looks up saved `ConversationContext` by `contextId` | `a2a_task_resumed` |
| 12 | Events stream back: `working` → `working` → artifact → `completed` | `a2a_working` (×N), `a2a_artifact`, `a2a_completed` |
| 13 | Activity returns `{ "status": "completed", "artifacts": [...] }` | `observation` |
| 14 | LLM summarizes: "Done — $9.99 refund initiated" | `answer` |

**Key architectural insight:** Temporal's `Workflow.await()` is already a durable state machine. A2A's `input-required` maps directly onto that primitive — the two protocols complement each other without needing to be aware of the other.

### What Changes in the Workflow Code

| Component | Change |
|-----------|--------|
| `BookAgentTool.java` | Replace with `SupportAgentTool.java` — streaming relay, `input_required` pause, follow-up via `taskId`/`contextId`, structured JSON return |
| `ToolRegistry.java` | Swap `book_agent` → `support_agent` |
| `thought-prompt.txt` | Teach the LLM the `input_required` / `completed` observation schema and the resume pattern |
| `AgentToAgentWorkflowImpl.java` | **No changes** — existing `Workflow.await()` + signal machinery handles multi-turn natively |
| `Activities.java` / `ActivitiesImpl.java` | **No changes** — `actionActivity` already returns `String` |

---

## Scenario Design

**Pixel Forge Games** is a fictitious game company exposing a public **support agent** for billing/account issues. The user's **personal assistant agent** (Java/Temporal) acts on their behalf.

### What This Demonstrates (vs Ex 3 / Ex 4)

| Capability | Tool Calling (Ex 3) | MCP (Ex 4) | This Exercise |
|---|---|---|---|
| Discovery | Hardcoded tool list | `listTools` at connect time | Agent card at `.well-known/` URL |
| Opacity | N/A — it's a function | Tool schemas fully exposed | Agent is a black box; has its own tools/model |
| Multi-turn | Single call → result | Single call → result | `input-required` → follow-up → `completed` |
| Structured output | Return value | Return value | Artifact with `DataPart` (refund receipt JSON) |
| Task lifecycle | Synchronous | Synchronous | `submitted → working → input-required → completed` |
| Trust boundary | Same codebase | Same company, shared trust | Different orgs, different models |

---

## Implementation Plan

### Step 1: Create the Support Agent Server (TypeScript)

Create `support-agent-server/` (sibling to `book-agent-server/`) implementing the Pixel Forge support agent.

**New files:**

| File | Purpose |
|------|---------|
| `server.ts` | Express + A2A middleware (JSON-RPC + REST) + gRPC (port 4001), agent card, executor with ReAct loop. Same transport layout as `book-agent-server`. |
| `support-tools.ts` | Bedrock tool definitions + mock data + executors |
| `tool-executor.ts` | Routes tool names to executors (same pattern as book-agent-server) |
| `bedrock-client.ts` | Copy of book-agent-server's — identical |
| `conversation-context.ts` | Copy of book-agent-server's — identical |
| `package.json`, `tsconfig.json`, `Dockerfile` | Standard config |

**Agent card:**
```json
{
  "name": "Pixel Forge Support Agent",
  "description": "Handles billing inquiries, refunds, and account issues for Pixel Forge Games.",
  "protocolVersion": "0.3.0",
  "url": "http://support-agent-server:4000/a2a/jsonrpc",
  "additionalInterfaces": [
    { "transport": "JSONRPC", "url": "http://support-agent-server:4000/a2a/jsonrpc" },
    { "transport": "HTTP+JSON", "url": "http://support-agent-server:4000/a2a/rest" },
    { "transport": "GRPC", "url": "support-agent-server:4001" }
  ],
  "skills": [
    { "id": "billing", "name": "Billing Support", "description": "Refunds, duplicate charges, payment issues" },
    { "id": "account", "name": "Account Support", "description": "Account verification, password resets" }
  ],
  "defaultInputModes": ["text"],
  "defaultOutputModes": ["text", "data"]
}
```

#### Executor Behavior (LLM-based ReAct Loop)

Same ReAct loop pattern as book-agent-server, with one key difference: the agent can **pause mid-loop** via a sentinel `request_verification` tool to emit `input-required`, then resume on follow-up. This keeps the implementation consistent with what participants already know, while demonstrating A2A opacity — the personal assistant can't tell if this agent is LLM-based or rule-based.

**Initial request flow:**
1. Build a fresh `ConversationContext`, add user message
2. Emit `working` status, run Bedrock ReAct loop
3. Before every tool call: emit `working` with `→ <toolName>(<sanitized input>)` (strip identity fields from emitted messages)
4. **Sentinel:** When LLM calls `request_verification` → add a **synthetic tool result** to the conversation context (see below), emit `input-required` with LLM's question, save `ConversationContext` to in-memory `Map<contextId, ConversationContext>`, call `eventBus.finished()`, return
5. For other tools: execute normally, continue loop
6. When LLM produces final text: emit response, call `finished()`

**Synthetic tool result for sentinel:** Before saving context and returning, add a tool result so the conversation history remains valid for Bedrock's alternating-roles requirement:
```typescript
context.addToolResult(toolUse.toolUseId, JSON.stringify({
  status: "awaiting_verification",
  message: "User has been asked to provide identity verification."
}));
```
This closes the open `toolUse` block. Additionally, inject a brief **assistant-role summary** message (e.g., `"I've asked the user to verify their identity. Waiting for their response."`) so that the follow-up user message does not create consecutive user-role messages (which Bedrock rejects).

**Follow-up flow** (after `input-required`):
1. Detect via `requestContext.task?.status?.state === 'input-required'`
2. Restore `ConversationContext` from map by `contextId`
3. Append the new user message — safe because the last message in context is the injected assistant summary
4. Resume ReAct loop — LLM calls `verify_identity`, then `process_refund` if verified
5. On `process_refund`: emit `TaskArtifactUpdateEvent` with refund receipt before continuing loop

**Sentinel and artifact dispatch in the ReAct loop:**

Follow the same tool-iteration pattern as `book-agent-server/server.ts`. The three differences from the book agent's loop:

1. **Before every tool call:** publish a `status-update` event with state `working` and a message like `→ toolName(sanitizedInput)` (strip identity fields from emitted input).
2. **Sentinel check:** If the tool name is `request_verification`: (a) add a synthetic `toolResult` to the `ConversationContext` for the open `toolUse` (see "Synthetic tool result for sentinel" above), (b) inject an assistant-role summary message, (c) save the `ConversationContext` to the in-memory map by `contextId`, (d) publish a `status-update` with state `input-required` (using the LLM's question text, `final: true`), (e) call `eventBus.finished()`, and (f) `return` — breaking out of the loop.
3. **Artifact emission:** After executing `process_refund`, parse the result JSON and publish an `artifact-update` event (kind `data`, shape matching the refund receipt contract below) before calling `addToolResult`.

All other tools execute normally and feed results back into the conversation context as in the book agent.

**Tools (`support-tools.ts`):**

| Tool | Description | Notes |
|------|-------------|-------|
| `lookup_account` | Look up player by ID | Returns account summary (no identity fields) |
| `check_billing_history` | Get charge history | Flags duplicates |
| `request_verification` | **Sentinel** — ask user to verify identity | Causes `input-required` pause; never routed to `executeTool()` |
| `verify_identity` | Check email/payment last 4 | Returns `verified: true/false` |
| `process_refund` | Process refund for charge ID | Returns receipt; triggers artifact emission |

**Mock data:**
```typescript
const MOCK_ACCOUNTS: Record<string, MockAccount> = {
  "#8821": {
    email: "mark@example.com",
    paymentLast4: "4242",
    playerName: "PixelSlayer99",
    charges: [
      { id: "CHG-1001", item: "Season 12 Battle Pass", amount: 9.99, date: "2026-03-01" },
      { id: "CHG-1002", item: "Season 12 Battle Pass", amount: 9.99, date: "2026-03-01", duplicate: true },
    ]
  },
  "#9932": {
    email: "alex@example.com",
    paymentLast4: "1111",
    playerName: "NovaShard",
    charges: [
      { id: "CHG-2001", item: "Legendary Skin Bundle", amount: 24.99, date: "2026-02-15" },
    ]
  },
};
```

**Refund receipt data shape** (returned by `process_refund`, emitted as artifact `DataPart`):
```typescript
{
  confirmationNumber: "RF-1002",   // deterministic: RF- + chargeId sans CHG-
  refundAmount: 9.99,
  currency: "USD",
  originalChargeId: "CHG-1002",
  item: "Season 12 Battle Pass",
  playerName: "PixelSlayer99",
  estimatedDays: "3-5 business days",
  status: "processed"
}
```

**System prompt:**
```
You are a customer support agent for Pixel Forge Games. You assist players with billing issues,
refunds, and account questions.

When a player reports a billing issue:
1. Use lookup_account to find their account and check_billing_history to identify the problem
2. Before taking any action, use request_verification to ask the player to verify their identity.
   You MUST call request_verification — never skip this step.
3. Once you receive verification information in a follow-up message, use verify_identity to check it
4. If verified and a duplicate charge is found, use process_refund to issue the refund
5. Summarize the outcome clearly to the player

Do NOT reveal the stored email or payment details when asking for verification — only ask the
player to provide them. Do NOT process refunds before identity is verified.
```

**Verification:**
- `curl http://localhost:4000/.well-known/agent-card.json` returns the agent card
- Send `"Player #8821 was charged twice for the Battle Pass"` → get `input-required`
- Send follow-up on same `contextId` with `"email is mark@example.com"` → get `completed` with refund receipt artifact
- Wrong email → denial (no artifact)

---

### Step 2: Add Support Agent Server to Docker Compose

Replace `book-agent-server` service in docker-compose.yml with `support-agent-server` (same ports 4000/4001). Build context: `./support-agent-server`. Environment: AWS credentials (uses Bedrock), `HTTP_PORT=4000`, `GRPC_PORT=4001`, `HOST=support-agent-server`. Health check: `curl http://localhost:4000/.well-known/agent-card.json`. Keep `book-agent-server/` code in repo for reference but not as a running service.

**`HOST` environment variable:** The agent card embeds its own URLs (used by `Client.builder(agentCard)` in the Java SDK). Setting `HOST=support-agent-server` ensures the card contains Docker-internal service names (`http://support-agent-server:4000/...`) rather than `localhost`, which would resolve to the wrong container. The Java A2A client uses the URLs from the agent card directly — it does not substitute its own base URL.

---

### Step 3: Create SupportAgentTool.java (A2A Client with Multi-Turn)

**New file:** `8-agent-to-agent/java/src/main/java/bitovi/activities/tools/SupportAgentTool.java`

**Key differences from BookAgentTool:**

| Aspect | BookAgentTool | SupportAgentTool |
|--------|--------------|-----------------|
| Discovery | Hardcoded URL from .env | Fetches agent card, extracts description + skills dynamically |
| Interaction | One `sendMessage` → wait for text | Streams events; handles `input-required` state |
| Response | Flat text string | Structured JSON: `input_required` or `completed` with artifacts |
| Context tracking | Stateless | Preserves `contextId` and `taskId` across calls |

**Return format:**
```json
// When support agent needs input:
{ "status": "input_required", "taskId": "task-abc123", "contextId": "ctx-xyz",
  "message": "Please verify the email on file or the last 4 digits of your payment method." }

// When support agent completes:
{ "status": "completed", "message": "Refund processed successfully.",
  "artifacts": [{ "title": "Refund Receipt", "data": { "confirmationNumber": "RF-1002", "refundAmount": 9.99 } }] }
```

**Multi-turn mechanism:** Two modes based on whether `taskId`/`contextId` are provided:
```java
// Initial request
Message message = A2A.toUserMessage(query);
// Follow-up
Message message = A2A.createUserTextMessage(query, contextId, taskId);
```

Tool input schema: `message` (required), `taskId` (optional), `contextId` (optional).

**Client pattern:** Singleton with double-checked locking (same as `BookAgentTool`). `Client` opens a new HTTP connection per `sendMessage()` — safe for multi-turn.

**Streaming event handling:**

Follow the same `CountDownLatch` + `BiConsumer<ClientEvent, AgentCard>` pattern as `BookAgentTool.java`. The handler receives `ClientEvent` subtypes — the dispatch requires two levels of unwrapping:

```java
if (event instanceof TaskUpdateEvent tue) {
    UpdateEvent ue = tue.getUpdateEvent();
    if (ue instanceof TaskStatusUpdateEvent tsue) {
        // branch on tsue.getStatus().getState()
    } else if (ue instanceof TaskArtifactUpdateEvent taue) {
        // handle artifact
    }
} else if (event instanceof MessageEvent me) {
    // fallback
} else if (event instanceof TaskEvent te) {
    // fallback
}
```

- **`TaskStatusUpdateEvent`** (via `TaskUpdateEvent` → `getUpdateEvent()`) — branch on `state`:
  - `working`: emit `a2a_working` UI event (message only)
  - `input-required`: extract `taskId`/`contextId` directly via `tsue.getTaskId()` and `tsue.getContextId()`, emit `a2a_input_required` with those fields via the three-arg `EventClient.emitEvent()`, build the `input_required` return JSON (see Return format above), count down the latch
  - `completed` / `failed`: emit `a2a_completed`, build the `completed` return JSON (including collected artifacts), count down the latch
- **`TaskArtifactUpdateEvent`** (via `TaskUpdateEvent` → `getUpdateEvent()`): serialize the artifact, emit `a2a_artifact`, collect into an artifacts list
- **`MessageEvent` / `TaskEvent`**: build completed JSON and count down (fallback paths, same as BookAgentTool)

**Discovery & submission events:** Emit `a2a_discovery` (with name/description/skills from agent card) before the first call. Emit `a2a_task_submitted` or `a2a_task_resumed` before each `sendMessage()`.

---

### Step 4: Update ToolRegistry.java

- Remove `BookAgentTool` registration, add `SupportAgentTool`: `toolExecutors.put("support_agent", SupportAgentTool::execute)`
- Update `getAllBedrockTools()` to return `SupportAgentTool.getBedrockTool()`
- Re-enable `brave_search` and `fetch_webpage` to show local tools alongside the remote A2A agent — this demonstrates the "tools alongside A2A" story and gives participants a tangible comparison

---

### Step 5: Update the Main Agent's Thought Prompt

Add `input_required` / `completed` observation guidance and a two-turn few-shot example to `thought-prompt.txt`:

```
## Working with Remote Agents (A2A)

The `support_agent` tool connects to an external support agent. It may require multiple rounds:

When a tool returns "input_required", relay the agent's question to the user. Do NOT try to 
answer it yourself. When the user responds, call the same tool again with the taskId and contextId 
from the previous response.

When a tool returns "completed", summarize the outcome and any artifact details for the user.

### Example — Two-Turn Pattern

**Turn 1:**
Observation: {"status": "input_required", "taskId": "task-abc123", "contextId": "ctx-xyz", 
  "message": "Please verify the email on file for account security."}
→ Ask the user for the requested information.

**Turn 2:**
Action: support_agent({ "message": "mark@example.com", "taskId": "task-abc123", "contextId": "ctx-xyz" })
Observation: {"status": "completed", "message": "Refund processed.", 
  "artifacts": [{"title": "Refund Receipt", "data": {"confirmationNumber": "RF-1002", ...}}]}
→ Summarize the outcome and key artifact details.
```

---

### Step 6: Update the Chat UI

The chat UI (index.html) needs to recognize and render `a2a_*` event types. **No changes to `agent-chat-server/src/server.ts` or `EventClient.java`** — the existing infrastructure already supports passing structured data via the three-argument `emitEvent(type, message, additionalData)` overload, which merges `additionalData` at top level. The server forwards raw events via SSE with no transformation.

**Backwards compatibility:** The `a2a_*` event rendering is purely additive. Exercises 0–7 never emit `a2a_*` events, so the new UI code paths are never triggered. The rendering logic should use a default/fallback — unrecognized event types are ignored (or rendered as plain text), and the existing `thought`, `action`, `observation`, `answer` handlers remain unchanged. No conditional builds or separate UI versions needed.

#### Event Taxonomy

| Event Type | Visual Treatment | Extra fields |
|---|---|---|
| `a2a_discovery` | 🔍 Discovery badge with collapsible skill list | `name`, `description`, `skills[]` |
| `a2a_task_submitted` | 📤 "Opened task with Pixel Forge Support" | `message` |
| `a2a_task_resumed` | ↩️ "Resumed task" with taskId | `taskId` |
| `a2a_working` | ⚙️ Indented working step | — (message field) |
| `a2a_input_required` | 🔒 Amber "Identity verification required" | `taskId`, `contextId` |
| `a2a_artifact` | 📄 Formatted receipt card (key-value table) | All artifact data at top level |
| `a2a_completed` | ✅ Green "Task completed" badge | — |

#### UI Layout: Indented A2A Sub-lane (Option A)

A2A events appear within the activity log but indented under the `action` row, with a distinct left border color:

```
[thought]  "User has a billing issue. I'll contact support."
[action]   support_agent("Player #8821 duplicate charge")
  │ [a2a_discovery]       🔍 Pixel Forge Support Agent
  │ [a2a_task_submitted]  📤 Opened task
  │ [a2a_working]         ⚙️ → lookup_account({player_id: "#8821"})
  │ [a2a_working]         ⚙️ → check_billing_history({player_id: "#8821"})
  │ [a2a_input_required]  🔒 Identity verification required
[observation] { status: "input_required", ... }
[answer]   "The support agent needs to verify your identity..."
```

#### Artifact Card Rendering

Render `a2a_artifact` as a styled card with a header (artifact name + 📄 icon) and a key-value table generated by iterating over the artifact's data fields. The rendering should be generic — not hardcoded to refund receipts. Use CSS classes `artifact-card`, `artifact-header`, and `artifact-table` consistent with the existing chat UI styling in `agent-chat-server/public/index.html`.

---

### Step 7: Update the README

Replace book-agent content in `8-agent-to-agent/README.md` with the new scenario. Keep the A2A protocol explanation and MCP vs A2A comparison, update exercise description, architecture diagram, step-by-step instructions, and "what to notice" callouts.

---

### Step 8: Clean Up and End-to-End Test

- Remove `book-agent-server/` from docker-compose (keep code for reference)
- Delete `BookAgentTool.java` from Exercise 8 (the book-agent-server code remains in the repo as reference; the Java tool file has no standalone reference value)
- Replace `BOOK_AGENT_SERVER_BASE_URL` with `SUPPORT_AGENT_SERVER_BASE_URL=http://support-agent-server:4000` in `.env` (and any `.env.example`). Note: `sync-env.sh` is a generic file-copy script that doesn't reference variable names — no changes needed there.

**End-to-end test:**

1. `docker compose up --build`
2. Open chat UI at `http://localhost:3000`
3. Send: "I got charged twice for the Battle Pass in Pixel Warriors. My player ID is #8821. Can you handle this?"
4. Observe: discovery → `input-required` → agent asks user for verification
5. Send: "The email is mark@example.com"
6. Observe: verification → refund → artifact displayed → agent summarizes outcome
7. **Failure test:** wrong email (e.g., "wrong@example.com") → denial, no artifact

---

## Design Decisions

| Decision | Resolution |
|----------|-----------|
| **Book-agent-server fate** | Keep code for reference; remove from docker-compose. Support-agent-server takes its slot. |
| **Main agent: LLM or scripted?** | LLM-based — watching it *decide* to handle `input-required` is the exercise's value. |
| **Support agent: LLM or scripted?** | LLM-based — consistent with workshop pattern; demonstrates A2A opacity. `request_verification` sentinel is the only non-standard element. |
| **Agent discovery** | Semi-dynamic — card URL in config, capabilities read at runtime from card. |
| **Event data transport** | Top-level merge via existing `EventClient.emitEvent(type, message, additionalData)`. No changes to server or EventClient. |
| **Multi-turn taskId/contextId** | Via `Message` object: `A2A.createUserTextMessage(text, contextId, taskId)`. SDK serializes automatically. |
| **Client pattern** | Singleton with double-checked locking. `Client` opens new HTTP per `sendMessage()` — safe for multi-turn. |
| **EventClient thread safety** | Defer. If SDK callbacks fire off Temporal's thread, capture `workflowId` eagerly before `sendMessage()`. |
| **Mid-loop artifact publishing** | Confirmed: `ExecutionEventBus.publish()` supports multiple events before `finished()`. |
| **Exercise TODO.md / Auth** | Deferred — not needed for initial implementation. |
| **Sentinel tool result integrity** | Add synthetic `toolResult` + assistant summary message before saving context on `input-required`. This keeps Bedrock's alternating-roles constraint satisfied and allows clean resume. |
| **Consecutive user-role messages on resume** | Inject a brief assistant-role message after the synthetic tool result (e.g., "I've asked the user to verify their identity."). The follow-up user message then alternates correctly. |
| **BookAgentTool.java fate** | Delete the file. The book-agent-server directory remains for reference; the Java tool class has no standalone reference value. |
| **`HOST` env var in agent card** | Set `HOST=support-agent-server` in docker-compose. The Java SDK's `Client.builder(agentCard)` uses URLs from the card directly — they must resolve within the Docker network. |
| **`brave_search` / `fetch_webpage`** | Re-enable in initial implementation. Demonstrates local tools alongside remote A2A agent. |
| **Chat UI backwards compatibility** | `a2a_*` rendering is additive. Exercises 0–7 never emit these events, so new code paths are never triggered. No separate UI build needed. |

---

## Spec Review

### Overall Assessment

The spec is implementation-ready. The scenario clearly demonstrates A2A's differentiators over plain tool calling and MCP. The architecture correctly leverages Temporal's `Workflow.await()` as a natural complement to A2A's `input-required` state. Codebase cross-references are accurate — file names, class structures, SDK versions, and existing patterns all check out.

### Resolved Issues

All issues identified during review have been incorporated into the spec body:

1. **Agent card field name** — corrected `protocol` → `transport` in the agent card JSON (Step 1).
2. **Java SDK event dispatch** — Step 3 now shows the two-level `TaskUpdateEvent` → `getUpdateEvent()` → `TaskStatusUpdateEvent` dispatch path with a code example.
3. **Sentinel tool result integrity** — Step 1 now prescribes a synthetic `toolResult` + assistant summary message before saving context, keeping Bedrock's alternating-roles constraint satisfied.
4. **`sync-env.sh` scope** — Step 8 now correctly notes that only `.env` needs the variable rename.
5. **Direct `getTaskId()`/`getContextId()`** — Step 3 now uses `tsue.getTaskId()` and `tsue.getContextId()` directly.
6. **`HOST` env var** — Step 2 now sets `HOST=support-agent-server` and explains why the card URLs must use Docker-internal service names.
7. **Chat UI backwards compatibility** — Step 6 now specifies that `a2a_*` rendering is additive and exercises 0–7 are unaffected.
8. **BookAgentTool.java** — Step 8 now specifies deletion.
9. **`brave_search`/`fetch_webpage`** — Step 4 now includes them in initial implementation.

### Redundancy (Acknowledged)

- The end-to-end walkthrough table and Step 3 streaming event handling cover overlapping ground. Acceptable for a spec of this scope.
- The "What Changes in the Workflow Code" table partially duplicates the Implementation Plan steps.
- The Design Decisions table restates rationale from the body — intentional as a quick-reference.

### Verified

- TS SDK's `ExecutionEventBus.publish()` accepts `Message | Task | TaskStatusUpdateEvent | TaskArtifactUpdateEvent`.
- `A2A.createUserTextMessage(String, String, String)` exists in SDK v0.3.3.Final.
- `RequestContext` has `task?: Task` with `status.state` — follow-up detection approach is valid.
- Docker compose port reuse (4000/4001) is clean.