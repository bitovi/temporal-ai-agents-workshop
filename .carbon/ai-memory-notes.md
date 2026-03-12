# AI Agent Memory Architecture

## Training Reference: LangMem, AgentCore, and Building Your Own with Temporal

---

## 1. Why AI Memory Matters

LLMs are stateless by design. Every API call starts fresh with no knowledge of prior interactions. Without a memory layer:

- Users repeat themselves every session
- Agents can't learn from past mistakes
- Personalization is impossible without custom logic in every prompt
- Context windows fill up quickly in long conversations

A **memory system** sits between the LLM and a storage backend and handles three operations:

1. **Store** — capture conversations and events as they happen
2. **Extract** — use the LLM to identify what's worth remembering long-term
3. **Retrieve** — fetch relevant memories when building future prompts

This training covers how to build that system, using LangMem (open-source Python) as a reference implementation, AWS Bedrock AgentCore Memory as a managed equivalent, and Temporal Workflows as the target Java implementation.

---

## 2. The Architecture We're Building

This is the **async / background processing model** — the agent itself does not manage memory. Memory is handled entirely by a separate async pipeline.

```
┌─────────────────────────────────────────────────────┐
│                  Agent / API Layer                   │
│                                                     │
│  Receives user message                              │
│    → Retrieves relevant long-term memories          │
│    → Builds prompt: [system + memories + history]   │
│    → Calls LLM                                      │
│    → Returns response                               │
│    → Fires conversation event to memory pipeline    │
└──────────────────────┬──────────────────────────────┘
                       │ fire-and-forget
                       ▼
┌─────────────────────────────────────────────────────┐
│              Memory Pipeline (Async)                 │
│                                                     │
│  Receives conversation event                        │
│    → Waits N seconds (debounce / let it settle)     │
│    → Searches store for existing related memories   │
│    → LLM extracts what's worth keeping              │
│    → LLM deduplicates / merges with existing        │
│    → Writes final memory records to store           │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│                  Memory Store                        │
│                                                     │
│  Namespace-partitioned key-value store              │
│  Records indexed with vector embeddings             │
│  Queryable via semantic similarity search           │
└─────────────────────────────────────────────────────┘
```

**Key design principle:** Current session context stays in Temporal Workflow state. The memory store only handles cross-session long-term memories. The pipeline is entirely decoupled from the agent's hot path.

---

## 3. Memory Types

### 3.1 Semantic Memory

**What it stores:** Facts, knowledge, relationships
**Examples:** "The customer's account number is 48291", "The team uses PostgreSQL", "User prefers metric units"
**Triggered by:** Background extraction after conversation
**Retrieval:** Semantic similarity search
**Schema:** Defined by you — any structured record works

```json
{
  "kind": "SemanticMemory",
  "content": {
    "fact": "User prefers dark mode in all UIs",
    "confidence": "high",
    "source": "repeated across 3 sessions"
  }
}
```

### 3.2 User Preference Memory

**What it stores:** Recurring patterns in user behavior, choices, communication style
**Examples:** "Always requests window seat", "Prefers bullet points over paragraphs", "Size 10 shoes"
**Note:** This is not a different code path from semantic memory — it's just semantic memory with a preference-shaped schema. The distinction is in what you ask the LLM to extract, not how the pipeline works.

```json
{
  "kind": "UserPreference",
  "content": {
    "category": "communication",
    "preference": "concise_bullet_points",
    "context": "User has corrected verbose responses in 4 sessions"
  }
}
```

### 3.3 Episodic Memory

**What it stores:** Compressed narratives of past conversations
**Examples:** "On March 10th, user was troubleshooting a failed DB migration. Resolution: rolled back to v2.3.1"
**Triggered by:** End of conversation / session close
**Retrieval:** Recency + semantic search
**Schema:** Title + summary (can be extended)

```json
{
  "kind": "EpisodicMemory",
  "content": {
    "title": "Database migration failure - March 10",
    "summary": "User attempted to run migration v2.4.0. Foreign key constraint failed on orders table. Resolved by rolling back to v2.3.1 and filing bug report.",
    "session_id": "sess-8821",
    "timestamp": "2026-03-10T14:32:00Z"
  }
}
```

### 3.4 Episodic Memory with Reflections (AgentCore)

AgentCore extends episodic memory with a **reflections layer** — a secondary extraction pass that runs across multiple episodes to surface cross-session patterns.

Each episode captures a richer structure:

| Field             | Description                                  |
| ----------------- | -------------------------------------------- |
| **Situation**     | What was the context or scenario             |
| **Intent**        | What the user/agent was trying to accomplish |
| **Assessment**    | How the situation was evaluated              |
| **Justification** | Why specific decisions were made             |

The reflections layer then asks: _across all past episodes, what patterns exist?_

- Which tool combinations consistently succeed for certain task types?
- What failure patterns repeat, and how were they resolved?
- What "lessons learned" should inform future behavior?

**This is agent learning** — the agent gets measurably better at its job over time by analyzing its own history. This is the most significant feature AgentCore has that LangMem doesn't include out of the box.

**Java/Temporal equivalent:** A separate scheduled Workflow that periodically runs a "reflection" pass over recent episodic memory records, producing a `ReflectionSummary` record stored back into the memory store.

### 3.5 Short-Term Memory (In-Session Context Management)

**Not long-term memory** — this is active context window management within a single session.

**Trigger:** When message history exceeds a token threshold
**Process:**

1. Older messages in the conversation are summarized into a single text block
2. The summary replaces the older messages
3. Recent messages are kept verbatim
4. The LLM sees: `[summary of earlier conversation] + [recent messages]`

**In a Temporal Workflow:** This is just state management in your Workflow — trim older messages from state and replace with a summary Activity result when state exceeds a size threshold. No external storage needed.

---

## 4. The Memory Extraction Pipeline (Deep Dive)

This is the core of the system. Understanding this is the key to building your own.

### 4.1 What Triggers Extraction

In the async model:

- Conversation event is submitted to the pipeline
- A configurable **delay** is applied (e.g., 10–30 seconds) to let the conversation "settle" — if more messages arrive, the previous pending extraction is cancelled and a new one is scheduled
- After the delay, extraction begins

**Why the delay?** Prevents redundant extractions when a conversation is still in progress. Only process once the user has stopped interacting.

**In LangMem:** `reflector.submit(messages, after_seconds=30)` with per-conversation cancellation if a newer version arrives.
**In AgentCore:** Configurable trigger conditions (message count, idle timeout, token threshold).
**In Temporal:** A Workflow that starts a timer. If a new signal arrives before the timer fires, reset the timer. When timer fires, execute extraction Activities.

### 4.2 Phase 1 — Retrieve Existing Memories

Before the LLM sees anything, the pipeline fetches existing related memories from the store.

**Why:** The LLM needs to know what's already stored so it can update, merge, and deduplicate rather than creating duplicates.

```
Input: conversation messages
  → Generate search queries from conversation
  → Run semantic search against store
  → Retrieve top-K most relevant existing memories
Output: list of existing memory records with IDs
```

**In LangMem:** `store.asearch(namespace, query=conversation_slice, limit=10)` — runs multiple parallel searches using "dilated windows" (overlapping slices of the conversation) if no explicit query model is configured.

### 4.3 Phase 2 — LLM Extraction (Multi-Step)

The LLM acts as an **intelligent editor** of the memory store — not just a reader. It receives a system prompt instructing it to operate in three phases:

**Phase A — Extract & Contextualize**

- Identify facts, preferences, and relationships worth keeping
- Add confidence levels to uncertain information
- Quote the source material from the conversation

**Phase B — Compare & Update**

- What's new vs. what's already in the store?
- Which existing memories need to be updated?
- Which are now outdated or contradicted?
- Which are redundant and should be merged?

**Phase C — Synthesize & Reason**

- What conclusions can be drawn?
- Are there patterns across facts?
- Qualify findings with confidence levels

The LLM expresses all of this by calling **structured tools**:

| Tool           | Purpose                       | Input                      |
| -------------- | ----------------------------- | -------------------------- |
| `CreateMemory` | Add a new memory record       | Memory schema instance     |
| `UpdateMemory` | Modify an existing record     | Memory ID + updated fields |
| `DeleteMemory` | Remove a stale/wrong record   | Memory ID                  |
| `Done`         | Signal extraction is complete | —                          |

### 4.4 Multi-Step Iteration

The extraction runs in a loop (1 to N iterations):

```
Iteration 1:
  Input:  system prompt + conversation + existing memories
  Tools:  CreateMemory, UpdateMemory, DeleteMemory (no Done yet)
  Output: initial set of memory operations

Iteration 2..N (if configured):
  Input:  previous output + tool results
  Tools:  All previous + Done
  Output: refined/consolidated operations, or Done signal

Loop ends when: Done is called, or no more tool calls are produced
```

The first iteration extracts. Subsequent iterations consolidate, deduplicate, and refine. In practice, 1–2 iterations is sufficient for most use cases.

### 4.5 Phase 3 — Persist to Store

After extraction, results are written to the store:

- Insert new records with fresh UUIDs
- Update existing records (preserving the same key/ID)
- Delete removed records
- Timestamps (`created_at`, `updated_at`) maintained on every record

---

## 5. The Memory Store

### 5.1 Data Model

Every memory record is stored as:

| Field        | Type        | Description                                           |
| ------------ | ----------- | ----------------------------------------------------- |
| `namespace`  | string path | Hierarchical partition key, e.g. `memories/user-123/` |
| `key`        | UUID string | Unique ID for this memory record                      |
| `value`      | JSON object | `{ kind: "MemoryType", content: { ...fields } }`      |
| `created_at` | timestamp   | When first written                                    |
| `updated_at` | timestamp   | Last modified                                         |
| `embedding`  | float[]     | Vector representation for semantic search             |

### 5.2 Namespace Design

Namespaces partition memories by tenant, user, team, or any other scope. They are hierarchical path strings.

**Examples:**

```
memories/user-alice/                  ← all of Alice's memories
memories/user-alice/preferences/      ← just Alice's preferences
org/acme/team/engineering/            ← team-scoped shared memories
org/acme/user/alice/session/sess-001/ ← session-specific
```

**Important:** Always use trailing slashes to prevent prefix collisions. `/user/alice` would accidentally match `/user/alice2`.

### 5.3 Semantic Search

At **write time:** The memory content is converted to a vector embedding (a high-dimensional float array representing meaning).

At **retrieval time:**

1. The search query is converted to a vector embedding
2. Cosine similarity is computed between the query vector and all stored memory vectors
3. Results are ranked by similarity score (0–1, higher = more relevant)
4. Top-K results returned

This enables **fuzzy / conceptual retrieval** — searching for "travel preferences" finds memories about seat choices, airline preferences, and hotel type even if none of those exact words appear in the query.

### 5.4 Store Interface (Conceptual)

```
put(namespace, key, value)           → write or overwrite a record
get(namespace, key)                  → retrieve single record by ID
search(namespace, query, topK)       → semantic similarity search
delete(namespace, key)               → remove a record
list(namespace, filter?)             → list records with optional filter
```

### 5.5 Storage Backend Options

| Option                         | Use Case                |
| ------------------------------ | ----------------------- |
| In-memory                      | Local development only  |
| PostgreSQL + pgvector          | Self-hosted production  |
| AWS Bedrock Memory (AgentCore) | Fully managed           |
| Pinecone / Weaviate / Qdrant   | Dedicated vector DB     |
| DynamoDB + OpenSearch          | AWS-native self-managed |

---

## 6. Retrieval: Building Prompts with Memory

This is where long-term memory is used. At the start of each request:

```
1. Take the user's incoming message (and optionally recent session history)
2. Run semantic search against the memory store
   → namespace: scoped to this user
   → query: the user's message / recent context
   → topK: 5–15 records depending on your context budget
3. Format results as a context block
4. Build final prompt:
   [System Instructions]
   [Long-term Memory Context]
   [Current Session History (from Temporal state)]
   [User's New Message]
5. Call LLM
```

**The memory context block looks like:**

```
--- Relevant Context from Memory ---
[Semantic] User prefers concise bullet-point responses (high confidence)
[Preference] User is in the EU, prefers metric units
[Episode] March 10: Helped with DB migration failure, resolved by rolling back to v2.3.1
------------------------------------
```

**Token budget management:** Each memory record should be compact. If you have a 2000-token memory budget, and each record averages 100 tokens, you can include ~20 records. The similarity score lets you cut off below a threshold (e.g., only include records with score > 0.75).

---

## 7. Temporal Workflow Implementation

### 7.1 Overview of Workflows and Activities

**Workflows** (durable, resumable orchestration logic):

- `MemoryExtractionWorkflow` — triggered after a conversation, handles the delay + extraction + persist cycle
- `EpisodicReflectionWorkflow` — scheduled periodic job that analyzes patterns across episodes

**Activities** (stateless, retriable units of work):

- `searchExistingMemories` — vector search against the store
- `extractMemoriesWithLLM` — call LLM with structured tools, parse tool calls
- `persistMemoryRecords` — write/update/delete records in the store
- `embedText` — convert text to vector embedding
- `summarizeThread` — single LLM call to produce episode title + summary
- `generateReflections` — analyze multiple episodes for patterns (AgentCore-equivalent)
- `compressSessionHistory` — summarize old messages when token threshold hit

### 7.2 MemoryExtractionWorkflow

```
Trigger: Signal or message received with { userId, sessionId, messages }

1. Start timer for N seconds (debounce delay)
   → If new signal arrives for same userId/sessionId: reset timer, update messages
   → This cancels stale work, just like LangMem's cancellation pattern

2. Timer fires → begin extraction
   Activity: searchExistingMemories(namespace, queryFromMessages, topK=15)
   → Returns: list of existing memory records with IDs

3. Activity: extractMemoriesWithLLM(conversation, existingMemories, schema)
   → LLM receives: system prompt + conversation + existing memories
   → LLM calls tools: CreateMemory / UpdateMemory / DeleteMemory
   → Returns: list of memory operations { op, id, content }

   If max_steps > 1: repeat with previous output as input until Done

4. Activity: persistMemoryRecords(namespace, operations)
   → For each operation:
     - Insert → put(namespace, newUUID, content)
     - Update → put(namespace, existingId, updatedContent)
     - Delete → delete(namespace, id)

5. Complete workflow
```

**Cancellation / debounce pattern:**

```java
// Pseudo-code for the debounce signal pattern
@WorkflowInterface
public interface MemoryExtractionWorkflow {
    @WorkflowMethod
    void run(String userId, String sessionId, List<Message> messages);

    @SignalMethod
    void addMessages(List<Message> newMessages); // resets the timer
}
```

### 7.3 LLM Tool Call Definition (JSON Schema)

The extraction LLM needs tools defined. Here's what those look like:

**CreateMemory Tool:**

```json
{
  "name": "create_memory",
  "description": "Store a new long-term memory record",
  "input_schema": {
    "type": "object",
    "properties": {
      "kind": {
        "type": "string",
        "enum": ["SemanticMemory", "UserPreference", "EpisodicMemory"],
        "description": "The type of memory being stored"
      },
      "content": {
        "type": "object",
        "description": "The memory content. Structure depends on kind.",
        "properties": {
          "fact": { "type": "string" },
          "confidence": { "type": "string", "enum": ["high", "medium", "low"] },
          "source": {
            "type": "string",
            "description": "Quote or reference from conversation"
          }
        }
      }
    },
    "required": ["kind", "content"]
  }
}
```

**UpdateMemory Tool:**

```json
{
  "name": "update_memory",
  "description": "Update an existing memory record by ID",
  "input_schema": {
    "type": "object",
    "properties": {
      "id": {
        "type": "string",
        "description": "The UUID of the existing memory record to update"
      },
      "content": {
        "type": "object",
        "description": "The updated content fields"
      }
    },
    "required": ["id", "content"]
  }
}
```

**DeleteMemory Tool:**

```json
{
  "name": "delete_memory",
  "description": "Remove a memory record that is outdated or incorrect",
  "input_schema": {
    "type": "object",
    "properties": {
      "id": {
        "type": "string",
        "description": "The UUID of the memory record to delete"
      },
      "reason": {
        "type": "string",
        "description": "Why this memory is being removed"
      }
    },
    "required": ["id"]
  }
}
```

**Done Tool:**

```json
{
  "name": "done",
  "description": "Signal that memory extraction and consolidation is complete",
  "input_schema": {
    "type": "object",
    "properties": {}
  }
}
```

### 7.4 System Prompt for Extraction LLM

```
You are a long-term memory manager for an AI assistant.

You will receive:
1. A conversation transcript
2. A list of existing memory records (may be empty)

Your job is to maintain a high-quality, deduplicated memory store by:

PHASE 1 — Extract & Contextualize
- Identify facts, preferences, and relationships worth keeping long-term
- Caveat uncertain information with confidence levels
- Reference the source material from the conversation

PHASE 2 — Compare & Update
- Identify what is new vs. already in the existing memories
- Update records that have new or changed information
- Remove records that are now outdated or contradicted
- Consolidate records that are redundant or overlapping

PHASE 3 — Synthesize
- Draw conclusions that aren't explicit but are clearly implied
- Identify patterns across the information
- Qualify all conclusions with appropriate confidence

Use the provided tools to create, update, and delete memory records.
All operations should be issued in a single parallel multi-tool call.
When you are satisfied with the memory store, call the done tool.
```

### 7.5 Temporal Mapping Table

| LangMem                                      | AgentCore                            | Temporal / Java                    |
| -------------------------------------------- | ------------------------------------ | ---------------------------------- |
| `ReflectionExecutor.submit(after_seconds=N)` | `CreateEvent` + trigger config       | Signal to Workflow + timer         |
| Background daemon thread                     | AWS managed async                    | Temporal Worker                    |
| Priority queue                               | Trigger conditions                   | Workflow timer / sleep             |
| Cancel pending task                          | `multitask_strategy=rollback`        | `workflow.cancel()` + new Workflow |
| `MemoryStoreManager.invoke()`                | Built-in strategy pipeline           | `MemoryExtractionWorkflow`         |
| `store.asearch()`                            | `RetrieveMemoryRecords`              | Activity: `searchMemories`         |
| `store.aput()`                               | `BatchCreateMemoryRecords`           | Activity: `persistMemory`          |
| `create_thread_extractor()`                  | `SummaryMemoryStrategy`              | Activity: `summarizeThread`        |
| Episodic reflections                         | `EpisodicMemoryStrategy` reflections | `EpisodicReflectionWorkflow`       |
| `summarize_messages()`                       | Session summary strategy             | Activity: `compressSessionHistory` |
| Namespace template                           | Namespace hierarchy + IAM            | DynamoDB partition key pattern     |

---

## 8. LangMem vs AgentCore: Corrected Comparison

> **Note on framing:** Both systems support the async background processing model described in this document. The "agent-driven tools" pattern (where the agent explicitly calls memory tools during a conversation) is an _alternative_ mode in LangMem — not how we're building this system. In the async background model, the two are architecturally equivalent.

### Where They're the Same (in async mode)

- Fire-and-forget event ingestion
- Background async extraction pipeline
- LLM-based extraction with structured output
- Deduplication and consolidation
- Namespace-scoped key-value store
- Vector embeddings for semantic search
- Retrieval at prompt-build time

### Real Differences

| Aspect                     | LangMem                                        | AgentCore                                                                    |
| -------------------------- | ---------------------------------------------- | ---------------------------------------------------------------------------- |
| **Infrastructure**         | You provision (vector DB, embeddings, scaling) | AWS manages everything                                                       |
| **Episodic Reflections**   | Not built-in                                   | Yes — cross-episode pattern analysis                                         |
| **Multi-tenant isolation** | Custom namespace logic                         | Native IAM at namespace granularity                                          |
| **Cancellation model**     | Cancel-by-thread-ID, replace-if-newer          | `multitask_strategy=rollback`                                                |
| **Customization**          | Complete — any schema, any LLM logic           | Built-in strategies with prompt overrides, or fully custom via SNS/S3/Lambda |
| **Cost model**             | Self-hosted infrastructure                     | AWS pay-per-use                                                              |
| **Framework lock-in**      | LangGraph-optimized, portable                  | Framework-agnostic                                                           |

### AgentCore's Self-Managed Strategy (The Temporal Analogy)

For maximum control, AgentCore has an escape hatch called a **self-managed strategy**. When triggered, it publishes a payload to SNS/S3 and your own infrastructure processes it:

```
CreateEvent (sync)
  → AWS trigger fires
  → SNS notification published
  → S3 payload delivered: { jobId, sessionId, currentContext, historicalContext }
  → Your Lambda / ECS processes it
  → BatchCreateMemoryRecords / BatchUpdateMemoryRecords called with results
```

**This is the direct AgentCore equivalent of your Temporal Workflow** — same pattern, different execution substrate.

---

## 9. The One Unique Feature Worth Adding: Episodic Reflections

The reflections layer in AgentCore's episodic memory is the most novel concept here — it has no direct equivalent in LangMem out of the box. It's worth including in your training because it represents a meaningful step toward true agent learning.

### What Reflections Do

Standard episodic memory: stores _what happened_
Reflections layer: stores _what we learned from what happened_

After enough episodic memories accumulate, a second-pass analysis runs:

```
Input: N recent episodic memory records

Questions the LLM answers:
- Across these episodes, which approaches consistently succeeded for [task type]?
- What tool combinations worked best?
- Which failure patterns recurred, and how were they resolved?
- What should the agent do differently next time it encounters [situation]?

Output: ReflectionSummary records stored in the memory store
```

### Java/Temporal Implementation

```
EpisodicReflectionWorkflow (scheduled, e.g., daily or after every 10 episodes):

1. Activity: listRecentEpisodes(namespace, since=lastRunTimestamp, limit=50)
2. Activity: generateReflections(episodes)
   → LLM analyzes patterns across all episodes
   → Produces structured ReflectionSummary records
3. Activity: persistReflections(namespace, reflections)
4. Update lastRunTimestamp
```

**Reflection record schema:**

```json
{
  "kind": "Reflection",
  "content": {
    "pattern": "Database migration failures are always preceded by schema drift warnings",
    "recommendation": "Always check schema diff before running migrations",
    "confidence": "high",
    "evidence_count": 4,
    "first_observed": "2026-02-14",
    "last_observed": "2026-03-10"
  }
}
```

Reflections are retrieved the same way as any other memory — via semantic search at prompt-build time. They effectively encode the agent's accumulated experience as queryable knowledge.

---

## 10. End-to-End Request Flow (Putting It All Together)

```
Incoming user message
│
├─► RETRIEVAL (sync, hot path)
│   1. Embed the user's message
│   2. search(namespace=user-123/, query=message, topK=10)
│   3. Format top results as memory context block
│   4. Build prompt: [system] + [memory context] + [session history] + [message]
│   5. Call LLM → stream response to user
│
└─► EXTRACTION (async, background)
    1. Append message + response to session log
    2. Signal MemoryExtractionWorkflow (userId, sessionId, updatedMessages)
       → If workflow already running for this session: signal resets the timer
       → If not running: start new workflow instance
    3. Return (don't wait)

Meanwhile, in Temporal:
    MemoryExtractionWorkflow:
    → Waits 30 seconds (debounce)
    → If new signal: reset wait
    → After wait: run extraction pipeline
    → Memories written to store
    → Available for future requests
```

---

## 11. Suggested Slide Structure

| #   | Slide Title                        | Key Points                                        |
| --- | ---------------------------------- | ------------------------------------------------- |
| 1   | The Stateless LLM Problem          | Why memory matters, what happens without it       |
| 2   | Memory System Overview             | The three operations: store, extract, retrieve    |
| 3   | Our Architecture                   | Diagram: agent layer + async pipeline + store     |
| 4   | Memory Types                       | Semantic, Preference, Episodic, Short-term        |
| 5   | The Storage Model                  | Namespace + key/value + embeddings                |
| 6   | Semantic Search Explained          | How vector similarity enables fuzzy retrieval     |
| 7   | The Extraction Pipeline            | Retrieve existing → LLM extracts → persist        |
| 8   | LLM as Memory Editor               | The 3-phase system prompt, tool calling loop      |
| 9   | Tool Definitions                   | CreateMemory / UpdateMemory / DeleteMemory / Done |
| 10  | Async Background Processing        | Debounce, delay, cancel-and-replace pattern       |
| 11  | Prompt Construction with Memory    | How retrieved memories are injected               |
| 12  | Temporal Workflow Design           | Workflows, Activities, signal/timer pattern       |
| 13  | AgentCore: The Managed Version     | Same concepts, AWS manages infrastructure         |
| 14  | Episodic Reflections               | Agent learning: what happened → what we learned   |
| 15  | Reflections as a Temporal Workflow | Scheduled analysis pass over episodic history     |
| 16  | Reference Implementation           | LangMem codebase pointers                         |

---

## 12. Reference: LangMem Key Files

| File                                                        | What It Contains                                                                 |
| ----------------------------------------------------------- | -------------------------------------------------------------------------------- |
| [extraction.py](src/langmem/knowledge/extraction.py)        | Core extraction engine: `MemoryManager`, `MemoryStoreManager`, system prompts    |
| [tools.py](src/langmem/knowledge/tools.py)                  | `create_manage_memory_tool`, `create_search_memory_tool`                         |
| [reflection.py](src/langmem/reflection.py)                  | `LocalReflectionExecutor`, `RemoteReflectionExecutor`, priority queue scheduling |
| [summarization.py](src/langmem/short_term/summarization.py) | `summarize_messages`, `SummarizationNode`, `RunningSummary`                      |
| [utils.py](src/langmem/utils.py)                            | `NamespaceTemplate`, `get_conversation`, `get_dilated_windows`                   |

## 13. Reference: AgentCore Key Concepts

| Concept                        | AgentCore Term                 | Notes                                          |
| ------------------------------ | ------------------------------ | ---------------------------------------------- |
| Async extraction trigger       | `CreateEvent`                  | Synchronous write, async processing            |
| Built-in semantic extraction   | `SemanticMemoryStrategy`       | Configured at memory creation                  |
| Built-in preference extraction | `UserPreferenceMemoryStrategy` | Configured at memory creation                  |
| Built-in episode summary       | `SummaryMemoryStrategy`        | Configured at memory creation                  |
| Episodic + reflections         | `EpisodicMemoryStrategy`       | Most advanced, includes cross-episode analysis |
| Custom pipeline                | Self-managed strategy          | SNS → S3 → Lambda → BatchCreateMemoryRecords   |
| Semantic retrieval             | `RetrieveMemoryRecords`        | Vector similarity, namespace-filtered          |
| User scoping                   | Actor ID + namespace           | IAM enforced                                   |
