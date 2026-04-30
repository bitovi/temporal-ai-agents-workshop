# Exercise 7 - Agent Memory

## Why Memory Matters

Every LLM has a finite context window, and LLMs themselves are stateless — every request stands alone. Without memory management, an agent either loses information as older context is discarded or hits token limits and fails entirely. A well-designed memory system lets an agent maintain continuity within a conversation, recall relevant information across sessions, and run indefinitely without degradation.

The context window is a dual constraint: it caps how much short-term history we can carry between turns _and_ how much long-term memory we can retrieve and inject on any given request. Every memory design decision in this exercise is ultimately about spending that limited budget well — something Temporal makes especially powerful because workflows can run for arbitrarily long periods, opening the door to agents that genuinely remember and adapt over time.

## Goals

By the end of this exercise, you should understand:

- Why the LLM context window creates the need for memory architecture
- The distinction between working context (what the agent sees now) and persistent memory (what it remembers)
- How short-term and long-term memory work together in a ReAct agent
- The difference between compaction (keeping context small) and memory persistence (extracting durable knowledge)
- How our implementation integrates memory retrieval and persistence into the Temporal workflow
- How AWS Bedrock AgentCore Memory provides an off-the-shelf solution for memory strategies
- Practical concerns: cold starts, token budgets, privacy, and cost

## What You Need to Know

### Context vs. Memory

This is the most important distinction in agent memory architecture.

**Working context** is the full payload assembled and sent to the LLM on a given turn:

- The **system prompt** — how the agent should act and respond.
- The **tool definitions** — actions the agent is allowed to take.
- The **short-term memory** of the current session: recent user/assistant turns and tool results.
- **RAG chunks** of documents fetched based on the current conversation.
- **Retrieved long-term memory** records (preferences, facts, summaries) injected for this turn.

In our implementation, the durably-tracked slice is the `List<ContextEntry>` (short-term memory). The system prompt, tool definitions, RAG chunks, and retrieved LTM records are reassembled on every LLM request. Everything together must fit in the context window, and it exists only for the lifetime of the current workflow execution.

**Persistent memory** is what the agent remembers across sessions — durable knowledge stored externally in a database, vector store, or managed service like AgentCore Memory. Because it lives outside the workflow, it survives workflow failures, restarts, history compaction, and `continueAsNew`. The agent cannot see it directly; it must be explicitly retrieved and injected. That can happen two ways: the agent calls a memory-lookup **tool** during reasoning, or — more commonly — the workflow/activity layer fetches relevant memories **automatically** before each thinking step.

The bridge between them is **memory retrieval**: at the start of each thinking step, the agent queries persistent memory and injects the most relevant records into the working context. This is the "Pre-Retrieval (RAG)" pattern — conceptually identical to RAG from earlier exercises, except the "documents" are memories the agent itself produced. The same idea applies to other architectures (e.g., a Plan & Execute agent retrieves before planning).### Short-Term Memory Architecture

Short-term memory (STM) is the rolling window of recent interactions the agent carries in its working context to maintain continuity throughout a single session. Key design decisions:

- **Window size.** How many recent turns to keep (e.g., the last 5–10 turns). Our implementation keeps all entries until compaction is triggered.
- **Checkpointing.** Durably persisting session state so a mid-conversation crash doesn't lose the whole interaction. This is the durable execution problem, and Temporal Workflows solve it for free — state is checkpointed reliably after every Activity completion. Without Temporal, you'd typically reach for Redis or SQLite and implement your own save/reload logic.
- **Time-to-Live (TTL).** Ephemeral or session-scoped items can expire automatically when no longer relevant.

Checkpointing in Temporal example:

```java
public void receiveMessage(MessagePayload payload) {
    pendingMsgs.add(payload);
}

public WorkflowResult execute(WorkflowInput input) {
    List<String> context = new ArrayList<>();
    List<String> persist = new ArrayList<>();
    Workflow.await(() -> !pendingMsgs.isEmpty());
    while (true) {
        // Process all pending messages
        if (!pendingMsgs.isEmpty()) {
            for (MessagePayload msg : pendingMsgs) {
                context.add(formatUserMessageContext(msg));
                persist.add(formatUserMessageContext(msg));
            }
            pendingMsgs.clear();
        }

        ThoughtResponse thoughts = activities.thoughtActivity(context);
        if (thoughts.type().equals("answer")) {
            persist.add(formatUserMessageContext(thoughts.answer()));
            // Persist memory entries for this turn (usually just the USER_MESSAGE and ANSWER)
            activities.persistMemoryActivity(persist);

            // Broadcast the answer to whatever client is listening (e.g. frontend, CLI, etc.)
            activities.broadcastMessageActivity(thoughts.answer());

            // Once this has been persisted, we can clear the persist buffer for the next turn
            persist.clear();
        }

        if (thoughts.type().equals("action")) {
            // Handle action (not shown in this example)
        }
    }
}
```

And a simplified memory persistence Activity:

```java
public void persistMemoryActivity(List<ContextEntry> entries, String sessionId) {
    // Send the messages to the AgentCore memory extraction system.
    try {
        AgentCoreMemory.createEvent(entries);
    } catch (Exception e) {
        System.err.println("Error in AgentCoreMemory.createEvent: " + e.getMessage());
        throw ApplicationFailure.newFailure("AgentCoreMemory.createEvent failed: " + e.getMessage(),
                "PersistAgentCoreMemoryActivityError");
    }
}
```

Getting this kind of durability for free is one of the reasons Temporal is such a strong fit for AI agents. Because Temporal guarantees durable execution and the LLM is stateless, the Workflow Execution becomes our source of truth: the working context lives in workflow state, and every Activity call is persisted in event history. If the worker crashes, the LLM provider goes down, or the host loses power, nothing is lost — Temporal replays and rebuilds the same state.

#### LangChain / LangGraph

LangChain/LangGraph are useful comparison points for how STM and checkpointing look without Temporal. LangGraph offers state persistence through **Savers** — each time the agent transitions between nodes in the graph, the persistence layer saves agent state. Out of the box: an in-memory Saver (testing), Postgres, and Redis. The trade-off is scope: LangGraph's durability is focused on the agent state itself, narrower than Temporal's, where the entire Workflow Execution (activities, results, retries, timers, signals) is durable.

### Long-Term Memory Architecture

Long-term memory (LTM) lets the agent recall context across sessions — user preferences, historical behavior, summaries of past conversations.

The foundation of scalable LTM is RAG using a vector database to store embeddings of prior interactions. This is the same pattern from [Exercise 2](../2-rag/README.md); the only difference is that the "documents" are memories the agent itself produced:

1. **Store discrete units** — individual interactions, LLM responses, or extracted facts — not whole summarized sessions.
2. **Vectorization.** Embed those units so they can be retrieved by semantic similarity even when wording differs.
3. **Hybrid retrieval.** Combine semantic search with metadata filtering and ranking. Memory has much richer metadata than basic RAG: `userId`, `sessionId`, strategy type, LLM-generated categories, and timestamps — all usable to influence retrieval.

   Recency is the most important example. If the user said their favorite color was red six months ago and blue last week, a purely semantic search treats both records as roughly equivalent. For a long-running agent, that's a real problem. Hybrid retrieval addresses it with a scoring function that blends semantic similarity, age, strategy type, and other metadata, tuned to the use case.

   Two common ways to combine metadata with semantic search:
   - **Pre-filtering.** Cut down the search space _before_ the vector search runs (most vector DBs support this natively). For example, exclude memories from the current session (already in STM — retrieving them wastes context) or restrict to the last N days. Efficient because the index only scores candidates that passed the filter.
   - **Post-filtering / re-ranking.** Cast a wide semantic net first, then re-rank in application code (e.g., fetch top 50 and bias toward recency). Easier to retrofit onto an existing RAG pipeline.

   Production systems typically pre-filter by hard constraints (`userId`, strategy, TTL) and post-rank survivors by a blended similarity-plus-recency score.

### Compaction vs. Memory Persistence

These are complementary but distinct operations.

**Compaction** keeps the current session's working context within some limit. That limit could be the model's full window, but in practice you'll set a smaller bound to control cost and leave headroom for system instructions, tool definitions, and retrieved memories. When the context grows past that bound, the agent summarizes it and discards the originals. Compaction is **lossy by design**.

Two pressures push us to compact: the LLM context window, _and_ Temporal's Workflow Event History (which has hard caps on event count and total size). The standard solution is `continueAsNew`, which starts a fresh workflow run while carrying forward a compacted snapshot. So compaction serves both: shrinking the working context to fit the LLM, and shrinking carried-forward state so we can `continueAsNew` cleanly.

#### Refresher: Temporal Continue-As-New

Continue-As-New lets a workflow effectively run forever. It checkpoints state, ends the current Workflow Execution, and starts a fresh one in its place. Two main reasons to reach for it:

- **Size and performance limits.** A long Event History will eventually hit Temporal's limits.
- **Workflow versioning.** A long-running workflow that started on older code can run into versioning issues; a new Execution picks up the current code path cleanly.

Carried-forward state is passed as arguments to the new Execution. The new run keeps the **same WorkflowId**, gets a **new RunId**, and begins its own Event History from scratch. From the outside it looks like one continuous, infinitely long workflow.

**Memory persistence** extracts durable knowledge _before_ that information would be compacted away. Rather than summarizing into a single blob, persistence uses AI to identify specific facts, preferences, and patterns worth remembering, and stores them in a structured external system.

The ordering matters: our workflow persists to memory after each answer (while full detail is still available), and compaction happens later (when `continueAsNew` triggers). Important information is extracted at full fidelity before compression reduces it.

### Memory Strategy Types

Modern memory systems use multiple specialized strategies. Each AgentCore built-in strategy follows the same Extraction → Consolidation pipeline; what differs is the prompt, output schema, and what kind of information it captures.

**Semantic memory** extracts factual information and contextual knowledge from conversations, building a persistent knowledge base of entities, events, and key details. Output is a list of standalone facts (JSON, one fact per record). Best for stable facts and domain knowledge. Only USER and ASSISTANT messages are processed.

- "The user lives in Austin, Texas"
- "The user is a software engineer"

**User preference memory** extracts preferences, choices, and styles — typically inferred from patterns across conversations rather than stated outright. Best for personalizing responses and adapting tone.

- "Prefers Java over TypeScript"
- "Likes outdoor dining"

**Episodic memory** identifies important moments, summarizes them into compact records, and organizes them so the system can retrieve what matters without noise. Captures the flow of events — what was tried, what worked, what was learned. Best for learning from past problem-solving attempts and building procedural knowledge.

**Summary memory** generates condensed, real-time summaries within a single session, capturing key topics, tasks, and decisions. Output is XML where each `<topic>` represents a distinct area; a session can have multiple chunks that together form the complete summary. Retrievable by namespace via `ListMemoryRecords` or by semantic search via `RetrieveMemoryRecords`. Best for quickly re-establishing context from previous sessions.

Summary differs most from the others because it is **session-scoped** — it depends on `sessionId` to know what counts as one conversation. In our implementation `sessionId` maps to the Temporal `WorkflowId`, so a new chat (new workflow) produces a fresh summary. You could also define a session by an idle timer (e.g., new logical session after 30 minutes of inactivity).

A well-architected system uses multiple strategies simultaneously: semantic facts and preferences for relevance, episodic memories for similar situations, summaries for broad continuity. The retrieval step queries across all of them.

#### Custom Strategies

For advanced cases, AgentCore lets you override a built-in strategy with a **Custom strategy** — your own extraction and/or consolidation prompts and, optionally, a different LLM. Useful for domain-specific extraction (e.g., only capture facts about a particular product line).

## Implementing Memory in a Temporal Agent

A from-scratch memory system needs two parts:

1. **Core Extraction Layer.** An LLM-powered function that takes conversation messages plus existing memories and returns a list of memory operations (Add, Update, Delete, No-op).
2. **Stateful Memory Store.** Wraps the core layer: searches existing memory for relevant records to feed into extraction, then executes the operations. Can be built on any storage — vector, graph, or relational.

### Asynchronous Memory Persistence with Temporal Workflows

We don't want to block the agent's response on memory extraction and consolidation. Instead, we run a separate Temporal Workflow asynchronously: it takes conversation history as input, runs extraction, and updates the long-term store — letting the agent respond immediately. Short-term memory and the existing working context cover the current turn while LTM is updated in the background for future interactions.

If the working context gets close to its limit, we can flip this to synchronous right before compaction so we extract as much as possible before details are lost.

The shape of the workflow:

1. The user sends a message; the agent generates a response from the existing agent workflow logic.
2. Once an answer is reached, we signal a `MemoryExtractionWorkflow` with the user message and assistant message.
3. That workflow keeps a timer running, collecting messages from the ongoing conversation.
4. After a period of inactivity, it wakes up and runs memory extraction over everything it has collected.
5. Resulting memories are persisted to the long-term store.

The timer also acts as a debounce: extraction runs only when the user has finished their current line of thought.

### Memory Extraction Activity

When the background `MemoryExtractionWorkflow` wakes up, it runs an Activity that takes the collected messages and runs them through the extraction function. The same pipeline can serve all strategy types (semantic, user preference, episodic, summary) by changing the prompt and output format.

**Step 1: Search for related existing memories.** Query the long-term store (vector or keyword search) using the new conversation messages. The retrieved memories give the extractor context to decide whether new info is genuinely new (Add), complementary (Update), or already covered (No-op).

**Step 2: Prepare the LLM prompt.** Provide instructions, the full conversation in XML tags (role + timestamp), the existing memories, and a set of memory-management tool definitions.

For reference, here's the prompt LangMem uses:

```md
You are a long-term memory manager maintaining a core store of semantic, procedural, and episodic memory. These memories power a life-long learning agent's core predictive model.

What should the agent learn from this interaction about the user, itself, or how it should act? Reflect on the input trajectory and current memories (if any).

1. **Extract & Contextualize**
   - Identify essential facts, relationships, preferences, reasoning procedures, and context
   - Caveat uncertain or suppositional information with confidence levels (p(x)) and reasoning
   - Quote supporting information when necessary

2. **Compare & Update**
   - Attend to novel information that deviates from existing memories and expectations.
   - Consolidate and compress redundant memories to maintain information-density; strengthen based on reliability and recency; maximize SNR by avoiding idle words.
   - Remove incorrect or redundant memories while maintaining internal consistency

3. **Synthesize & Reason**
   - What can you conclude about the user, agent ("I"), or environment using deduction, induction, and abduction?
   - What patterns, relationships, and principles emerge about optimal responses?
   - What generalizations can you make?
   - Qualify conclusions with probabilistic confidence and justification

As the agent, record memory content exactly as you'd want to recall it when predicting how to act or respond.
Prioritize retention of surprising (pattern deviation) and persistent (frequently reinforced) information, ensuring nothing worth remembering is forgotten and nothing false is remembered. Prefer dense, complete memories over overlapping ones.
```

In order to make the existing memories available to the LLM during extraction, we inject them into the prompt in a structured way:

```xml
<existing>
  <instance id=ed13a880-e437-4ea8-b175-5f147542b1b9 schema_type="PreferenceMemory">
    {'category': 'interface_preferences', 'preference': 'dark_mode', 'context': 'user prefers dark mode interface for all applications'}
  </instance>
  <instance id=a7b3c901-def4-5678-9012-abcdef123456 schema_type="Memory">
    {'content': 'User works at Acme Corp in the ML team'}
  </instance>
</existing>
```

**Step 3: Tool calling.** We provide tool definitions the LLM can choose to call. Generic `AddMemory` for new content; `UpdateMemory` and `RemoveMemory` take a `memoryId` to specify the target. One nice technique: generate tool calls _per fetched memory_, which makes Update/Delete easy because the LLM just picks the matching tool. A `Done` tool can signal completion.

Enable parallel tool calling so the LLM can perform multiple operations per response. Optionally, run multiple rounds (feed first-round outputs back in) so the LLM can iteratively refine until it calls `Done`.

Example tool definitions for each strategy type:

#### User Preference Memory Tool Definition

```json
{
  "name": "PreferenceMemory",
  "description": "Store the user's preference",
  "input_schema": {
    "type": "object",
    "properties": {
      "category": { "type": "string" },
      "preference": { "type": "string" },
      "context": { "type": "string" }
    },
    "required": ["category", "preference", "context"]
  }
}
```

Example LLM Output

```json
{
  "name": "PreferenceMemory",
  "input": {
    "category": "user_interface",
    "preference": "prefers dark mode",
    "context": "User prefers dark mode for all applications and websites, especially during nighttime usage."
  }
}
```

#### Semantic Memory Tool Definition

```json
{
  "name": "SemanticMemory",
  "description": "Store a factual relationship between two entities. Use multi-tool calling to record multiple facts.",
  "input_schema": {
    "type": "object",
    "properties": {
      "subject": {
        "type": "string",
        "description": "The entity the fact is about"
      },
      "predicate": {
        "type": "string",
        "description": "The relationship or attribute"
      },
      "object": {
        "type": "string",
        "description": "The related entity or value"
      },
      "context": {
        "type": "string",
        "description": "Supporting context or source of this fact"
      }
    },
    "required": ["subject", "predicate", "object"]
  }
}
```

```json
[
  {
    "name": "SemanticMemory",
    "input": {
      "subject": "User",
      "predicate": "lives_in",
      "object": "San Francisco",
      "context": "Recently moved from NYC"
    }
  },
  {
    "name": "SemanticMemory",
    "input": {
      "subject": "User",
      "predicate": "works_at",
      "object": "Acme Corp",
      "context": "Joined the ML team"
    }
  }
]
```

#### Episodic Memory Tool Definition

```json
{
  "name": "EpisodicMemory",
  "description": "Capture a successful interaction pattern including the reasoning that made it work. Use multi-tool calling to record multiple episodes.",
  "input_schema": {
    "type": "object",
    "properties": {
      "observation": {
        "type": "string",
        "description": "The situation and relevant context — what happened"
      },
      "thoughts": {
        "type": "string",
        "description": "Key considerations and reasoning process that led to success"
      },
      "action": {
        "type": "string",
        "description": "What was done in response and how"
      },
      "result": {
        "type": "string",
        "description": "What happened and why it worked"
      }
    },
    "required": ["observation", "thoughts", "action", "result"]
  }
}
```

Example Output:

```json
[
  {
    "name": "EpisodicMemory",
    "input": {
      "observation": "User asked about binary trees. Mentioned familiarity with family trees.",
      "thoughts": "User has a concrete mental model (family trees) that maps well to the CS concept. Bridging to a known analogy will accelerate understanding.",
      "action": "Explained binary trees using family tree analogy: each parent has at most 2 children. Drew ASCII diagram with familiar names (Bob, Amy, Carl).",
      "result": "User immediately grasped the concept and independently extended the analogy to binary search trees ('organizing a family by age'). Analogies to known domains are effective for this user."
    }
  }
]
```

#### Example to remove a Memory by Id

```json
{
  "name": "RemoveMemory",
  "description": "Use this tool to remove (delete) a memory by its ID.",
  "input_schema": {
    "type": "object",
    "required": ["memoryId"],
    "properties": {
      "memoryId": {
        "type": "string",
        "description": "ID of the memory to remove. Must be one of: ('c3d551fa097b5ec09ad37057950fb0b1',)"
      }
    }
  }
}
```

#### Done Tool Definition

```json
{
  "name": "Done",
  "description": "Only call this tool once you are done forming & consolidating memories.",
  "input_schema": { "type": "object", "properties": {}, "required": [] }
}
```

**Step 4: Execute memory operations** against the long-term store.

### Memory Storage Architecture

Each stored memory record has:

- **memoryId** — unique identifier, used for updates and deletes.
- **namespace** — hierarchical path that categorizes the memory (e.g., `/strategies/semantic/actors/{actorId}/sessions/{sessionId}`). See the AgentCore configuration below for examples.
- **value** — the actual content. A simple text string for semantic memory; a structured JSON object for episodic memory.

### Agent Workflow Integration

The integration point for a custom memory system looks the same as the AgentCore integration we'll cover next: a `persistMemoryActivity` that sends each user message and assistant answer into the memory system after each turn. Only the implementation behind that Activity changes (custom extraction/consolidation pipeline vs. AgentCore's managed service).

In our custom implementation, `persistMemoryActivity` does two things:

- **Stores raw chat messages** in our database, tagged by `sessionId`. Useful because when we later build the extraction system prompt, we sometimes want additional recent messages from the current session as context.
- **Calls our local `createEvent`** instead of AgentCore's, which kicks off a Temporal Workflow to perform the actual extraction.

Because messages can come in fast, the extraction workflow batches multiple turns rather than running once per message. `createEvent` uses **Signal-With-Start**: if an extraction workflow is already running for this user, new messages are signaled in; otherwise a new one starts.

#### Memory Extraction Workflow

A simple structure:

- Started with a `userId` — memories belong to this specific user.
- The `WorkflowId` is deterministic from `userId` (which makes Signal-With-Start work — we always know which workflow to target).
- As the agent's chat session (its `RunId`) progresses, each user message and assistant response is signaled in.
- The workflow batches buffered events and passes them to an Activity that extracts semantic memories, along with the current `sessionId` and `userId` so results can be stored in an organized way.

This example only handles semantic memories; the same shape extends to other strategy types.

The Memory Extraction Workflow itself looks like:

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

In the agent's chat workflow, once the agent reaches an answer we persist the relevant entries:

```java
if (response.type().equals("answer")) {
    ContextEntry answer = ContextEntry.fromAnswer(response.answer());
    persist.add(answer);

    // Collect entries from most recent USER_MESSAGE to ANSWER
    activities.persistMemoryActivity(persist, Workflow.getInfo().getRunId());

    // The rest of the workflow continues here
}
```

The `persistMemoryActivity` saves the raw chat events locally and uses Signal-With-Start to feed them into the extraction workflow:

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

#### Semantic Memory Activity

The Memory Extraction Workflow invokes a `SemanticMemoryActivity` that runs the two LLM-driven stages we covered earlier — **Extraction** and **Consolidation** — and then writes the results to the vector store:

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

##### Step 1: Extraction

The extraction step finds **potential memories** from the chat entries that just came in.

1. **Fetch additional past conversation** for the current session — context around the latest messages. (This is why we store raw events in the first place.)
2. **Build the system prompt** with past and current conversation inserted into the template.
3. **Call Bedrock** to perform the extraction.
4. **Assemble structured results** — transform the returned JSON facts into `SemanticMemoryRecord`s.

###### Semantic Extraction Prompt

The prompt tells the model what to extract, passes in both prior and new conversation, and specifies the structured output schema.

```text
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

The extraction function itself wires up that prompt, calls Bedrock, and parses the JSON response into `SemanticMemoryRecord`s:

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

###### Semantic Extraction Schema

Intentionally simple: a JSON array of objects, each with a single `fact` field.

```json
{
  "description": "This is a standalone personal fact about the user, stated in a simple sentence.\nIt should represent a piece of personal information, such as life events, personal experience, and preferences related to the user.\nMake sure you include relevant details such as specific numbers, locations, or dates, if presented.\nMinimize the coreference across the facts, e.g., replace pronouns with actual entities.",
  "properties": {
    "fact": {
      "description": "The memory as a well-written, standalone fact about the user. Refer to the user's instructions for more information the prefered memory organization.",
      "title": "Fact",
      "type": "string"
    }
  },
  "required": ["fact"],
  "title": "SemanticMemory",
  "type": "object"
}
```

##### Step 2: Consolidation

The consolidation step takes those potential memories, checks them against existing memories, and decides whether to **add**, **update**, or **skip** each one.

1. **Find related existing memories** for each candidate via semantic search against the vector database.
2. **Build the consolidation payload** — a `toRelationXML` helper pairs each new candidate with its related existing memories.
3. **Call Bedrock** to emit a list of `AddMemory`, `UpdateMemory`, or `SkipMemory` operations.

###### Semantic Consolidation Prompt

The consolidation prompt asks the model to generate `AddMemory`, `UpdateMemory`, or `SkipMemory` entries for each candidate, specifying the fields required for each operation type.

```text
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

The consolidation step finds related existing memories for each candidate, builds the prompt, calls Bedrock, and parses the operations:

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

###### Semantic Consolidation Schema

Structured output makes the operations easy to parse. The prompt also includes the candidates from extraction along with the related existing memories pulled from the datastore.

```json
[
  {
    "memory": { "fact": "<content>" },
    "operation": "<AddMemory_or_UpdateMemory>",
    "update_id": "<existing_id_for_UpdateMemory>",
    "updated_memory": { "fact": "<content>" }
  }
]
```

Only include entries with `AddMemory` or `UpdateMemory` operations. Return an empty array if no changes are needed.

##### Step 3: Updating the Datastore

Apply the operations to the vector store via a `createVectorUpsertAsyncInput` helper that:

- **Computes the embedding** for each fact.
- **Builds a `PointStruct`** with the record ID (existing `memoryId` for updates, new for adds) and a `vectorPayload` carrying metadata.
- **Upserts** to the vector database.

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

After this step, new memories have been created and existing ones have been updated as needed.

##### Final Results

A quick end-to-end example. In a new chat with the agent, send:

> I'm Mark Repka and I need an example chat for a presentation for Riot Games. Could you help me!

The agent's reasoning and answer aren't the interesting part.

![Agent chat result](../.carbon/agent-chat-result.png)

A few seconds later, a new semantic memory shows up in the Qdrant collection — capturing that the user is working on a presentation for Riot Games.

![Memories in Qdrant](../.carbon/qdrant-memories.png)

Now start a brand-new chat and ask the agent what it knows about you. `retrieveMemoryRecordsActivity` pulls that semantic memory and feeds it into the Thought step. The agent responds with awareness of the Riot Games presentation — even though the new chat has zero conversation history of its own.

## How Memory Integrates with the Temporal Agent

Our Exercise 7 implementation extends the base ReAct workflow from Exercise 5 with two new Activities that bridge working context and persistent memory.

### The Memory-Augmented ReAct Loop

The key change from Exercise 5 is what happens at the start of THINKING: before the LLM reasons, the workflow first queries long-term memory to augment the context.

```
1. User sends message via Signal
2. Message is added to working context as a ContextEntry
3. Workflow transitions to THINKING
4. NEW: retrieveMemoryRecordsActivity queries AgentCore Memory
   - Current context is used as the search query
   - Returns relevant memory records (user preferences, facts, etc.)
5. thoughtActivity receives BOTH the working context AND retrieved memories
   - Context goes into {previousSteps} in the prompt
   - Memories go into {memoryRecords} in the prompt
6. LLM reasons with the full augmented context
7. If answer: persist to memory, transition to IDLE
8. If action: execute tool, observe, loop back to THINKING
```

In the workflow code:

```java
if (reactStep == ReactStep.THINKING) {
    // Step 1: Retrieve Long Term Memories
    String query = context.stream()
        .map(ContextEntry::toXmlString)
        .collect(Collectors.joining("\n"));
    RetrieveMemoryRecordsResult retrieveResult =
        activities.retrieveMemoryRecordsActivity(query, List.of(MemoryStrategyType.USER_PREFERENCE));
    List<String> memoryRecords = retrieveResult.memoryRecords();

    // Step 2: Think with augmented context
    ThoughtResponse thoughtResponse = activities.thoughtActivity(context, memoryRecords);

    // Step 3: If answer, persist the conversation to memory
    if ("answer".equals(thoughtResponse.type())) {
        // ... add answer to context ...
        // Batch persist: collect entries from most recent USER_MESSAGE to ANSWER
        if (foundUserMessage) {
            activities.persistMemoryActivity(entriesToPersist);
        }
        reactStep = ReactStep.IDLE;
    }
}
```

### Memory Retrieval Activity

`retrieveMemoryRecordsActivity` calls AgentCore's semantic search API with the current context as the query and a list of strategy types:

```java
public RetrieveMemoryRecordsResult retrieveMemoryRecordsActivity(
    String query, List<MemoryStrategyType> strategyTypes) {

    RetrieveMemoryRecordsResponse response =
        AgentCoreMemory.retrieveMemoryRecords(query, strategyTypes);

    // Format each record with XML tags indicating the strategy type
    for (MemoryRecordSummary summary : response.memoryRecordSummaries()) {
        MemoryStrategyType memoryStrategyType = AgentCoreMemory.getMemoryStrategyType(summary);
        String typeTag = memoryStrategyType.toString().toLowerCase().replace("_", "-");
        memoryRecords.add(String.format("<%s>%s</%s>", typeTag, text, typeTag));
    }
    return new RetrieveMemoryRecordsResult(memoryRecords);
}
```

Records are wrapped in strategy-typed XML tags (e.g., `<user-preference>Favorite color is blue</user-preference>`) and injected into the thought prompt's `{memoryRecords}` placeholder, alongside the conversation history.

The current implementation queries only `USER_PREFERENCE`. The TODO exercise encourages experimenting with other strategies (episodic, semantic, summary).

### Memory Persistence Activity

`persistMemoryActivity` sends conversation entries to AgentCore as events. After each answer, the workflow collects all entries from the most recent user message through the answer and persists them as a batch:

```java
public void persistMemoryActivity(List<ContextEntry> entries) {
    AgentCoreMemory.createEvent(entries);
}
```

Each `ContextEntry` is converted to a `Conversational` payload with its XML representation and role (USER or ASSISTANT). AgentCore then asynchronously processes these events through configured strategies, extracting facts, preferences, and summaries. Processing typically takes ~1 minute and requires no additional code.

The `sessionId` for memory events is set to the Temporal workflow ID, which remains stable across `continueAsNew` (only the _run ID_ changes). A long-running conversation keeps the same memory session even through `continueAsNew` boundaries, and extracted long-term memories persist across sessions under the same `actorId`.

### The Cold Start Pattern

One of the most compelling demonstrations of LTM is the cold start. When the agent starts a brand-new conversation (new workflow execution, empty `List<ContextEntry>`), it has zero history. But if the user has interacted before, `retrieveMemoryRecordsActivity` at the start of the first THINKING step returns relevant memories from past sessions.

The exercise TODO demonstrates this:

1. Start a conversation and tell the agent your preferences (favorite color, coffee, etc.)
2. Wait a few minutes for AgentCore to process events into long-term memories
3. Start a completely new conversation and ask "what do you know about me?"
4. The agent has no conversation history but can still answer from retrieved LTM records

This is what transforms an agent from a stateless tool into something that feels like it has a relationship with the user over time.

### Token Budget Allocation

The thought prompt allocates a fixed token budget across multiple sources:

- **System instructions and tool definitions** (fixed, ~500–2000 tokens).
- **Retrieved memory records** (variable; our implementation limits to 4 via `maxResults`).
- **Conversation history** (variable, grows with each turn).
- **Reserve for the model's response.**

The implementation uses `ModelUtils.truncateContextToTokenLimit` to fit the conversation and limits memory retrieval to 4 results. In production you'd want a more deliberate allocation — perhaps a fixed budget per source, dynamically adjusted based on what's available and relevant.

## AWS Bedrock AgentCore Memory

AgentCore Memory is a **fully managed AWS service** for long-term knowledge retention in AI agents. It collects memory events during agent interactions and processes them into structured long-term memories using different configurable strategies. These strategies define how to extract and store important information, organizing them by namespaces based on actorId and sessionId. When developing with AgentCore Memory the process is mostly automatic — after events are collected, the memory processing pipeline analyzes the conversations, extracts relevant facts and summaries using AI models, and stores them in a structured way. You don't need to build or manage any of this infrastructure yourself.

The Agent, when building up its next context, can query the long-term memory using the actorId and sessionId to retrieve relevant memories. This allows the agent to maintain context across sessions and provide more personalized responses without needing to manage complex memory infrastructure manually.

AgentCore Memory can be used with any Agent solution, including completely custom Agents, using the AWS SDK for JavaScript/TypeScript or Java.

### Retention

- **Raw short-term events** can be retained for up to **1 year** (configured via `eventExpiryDuration`; our workshop config uses 30 days).
- **Extracted long-term memories** persist **indefinitely** unless explicitly deleted.

### Pricing

At time of writing (verify against current AWS pricing):

- **Long-term memory storage (built-in strategies):** $0.75 per 1,000 memory records per month.
- **Long-term memory storage (built-in with override or self-managed strategies):** $0.25 per 1,000 memory records per month.
- **Long-term memory retrieval:** $0.50 per 1,000 retrievals.

### AgentCore Memory Resource

The memory resource is the central container. It encapsulates both raw events (STM) and processed long-term memories (LTM).

- **memoryId** : A unique identifier for the memory resource. Required for all read and write operations against AgentCore Memory.
- **actorId** : Identifies the entity associated with the memory (e.g., user, agent, project). Used with sessionId to enforce hierarchical namespaces and precise retrieval of relevant context.
- **sessionId** : Groups related memory events together during a single interaction. Essential for tracking the chronological narrative flow within a short-term conversation. In our implementation, this maps to the Temporal workflow ID.
- **Event (raw)** : An immutable record of an individual interaction (user prompt, agent reply, tool output). Constitutes the Short-Term Memory. These are stored chronologically in the memory resource.
- **Session Summary Object** : A durable, compressed, token-efficient distillation of an entire session, generated by an LLM upon session termination. Constitutes the primary artifact of Long-Term Memory, preserving context without exceeding the context window.

### Memory Strategies Configuration

Our implementation configures four strategies when creating the memory resource:

```java
CreateMemoryRequest request = CreateMemoryRequest.builder()
    .name("Riot_Bitovi_Temporal_AI_Workshop_Memory")
    .description("This is a temporary resource for the Temporal AI Agents Workshop (Part 2) delivered by Bitovi.")
    .eventExpiryDuration(30) // Events expire after 30 days
    .memoryStrategies(
        MemoryStrategyInput.builder()
            .episodicMemoryStrategy(EpisodicMemoryStrategyInput.builder()
                .name("Episodic")
                .description("Stores temporal sequences of events")
                .namespaces(List.of("/strategies/{memoryStrategyId}/actors/{actorId}/sessions/{sessionId}"))
                .reflectionConfiguration(EpisodicReflectionConfigurationInput.builder()
                    .namespaces(List.of("/strategies/{memoryStrategyId}/actors/{actorId}"))
                    .build())
                .build())
            .build(),
        MemoryStrategyInput.builder()
            .userPreferenceMemoryStrategy(UserPreferenceMemoryStrategyInput.builder()
                .name("Preference")
                .description("Tracks user preferences and choices")
                .namespaces(List.of("/strategies/{memoryStrategyId}/actors/{actorId}"))
                .build())
            .build(),
        MemoryStrategyInput.builder()
            .semanticMemoryStrategy(SemanticMemoryStrategyInput.builder()
                .name("Semantic")
                .description("Stores factual information and concepts")
                .namespaces(List.of("/strategies/{memoryStrategyId}/actors/{actorId}"))
                .build())
            .build(),
        MemoryStrategyInput.builder()
            .summaryMemoryStrategy(SummaryMemoryStrategyInput.builder()
                .name("Summary")
                .description("Maintains summarized conversation history")
                .namespaces(List.of("/strategies/{memoryStrategyId}/actors/{actorId}/sessions/{sessionId}"))
                .build())
            .build()
    )
    .build();
```

Strategies are configured at the **Memory Resource level**, which means a single resource's strategies are shared across all users (`actorId`s) and sessions (`sessionId`s) that write to it. Once enabled, they run automatically against every raw conversation event sent in via `CreateEvent`.

**Built-in strategies — pros:**

- AgentCore handles all extraction and consolidation automatically using predefined, optimized algorithms.
- No configuration required beyond basic settings (namespaces, triggers).
- Suitable out of the box for standard conversational AI use cases.

**Built-in strategies — cons:**

- Limited customization (extraction prompts and behavior are fixed).
- Higher per-record storage cost than a comparable DIY solution backed by your own database.

Bedrock AgentCore also offers Custom memory strategies that let you choose a specific LLM and override the prompt for extraction and consolidation to your specific domain or use case. For example, you might want to append to the semantic memory prompt so that it only extracts specific types of facts or memories.

Custom strategy documentation: https://docs.aws.amazon.com/bedrock-agentcore/latest/devguide/memory-self-managed-strategies.html#use-self-managed-strategy

### Processing Pipeline

Most built-in AgentCore strategies are organized around two LLM-driven stages. These terms are not unique to Bedrock — the same Extraction → Consolidation pattern shows up in libraries like LangMem, Mem0, Zep, and any DIY system you'd build yourself.

**Stage 1: Extraction.** Looks at the recent conversation events (and any prior context provided to the strategy) and pulls out **potential memories** — candidate facts, preferences, or episodes worth remembering. These are not yet committed to the store; they're just what the model thinks might be worth keeping.

**Stage 2: Consolidation.** Takes those candidates and queries the existing memory datastore for any related records. The LLM then decides, for each candidate, what to actually do against the store. The output is a structured list of operations:

- **Create** a new memory record (the candidate is genuinely new).
- **Update** an existing record (the candidate adds detail or refines what's already there).
- **Skip** the candidate (it's redundant, irrelevant, or low value).

Around those two stages, the full pipeline includes:

1. **Conversation Analysis:** Saved conversations are analyzed based on configured strategies.
2. **Information Extraction:** Important data (facts, preferences, summaries) is extracted using AI models.
3. **Structured Storage:** Extracted information is organized in namespaces for efficient retrieval.
4. **Semantic Indexing:** Information is vectorized for natural language search capabilities.
5. **Consolidation:** Similar information is merged and refined over time

Processing Time: Typically takes ~1 minute after conversations are saved, with no additional code required.

Behind the scenes, the pipeline uses AI-powered extraction with foundation models, creates vector embeddings for similarity-based retrieval, structures information using configurable path-like hierarchies, automatically consolidates similar information to prevent duplication, and continuously improves extraction quality based on conversation patterns.

Important: For semantic and user preference memory strategies, only USER and ASSISTANT role messages are processed for long-term memory extraction. Messages with other role types are skipped. For the summary strategy, all roles are processed.

### CreateMemoryResource

Before this workshop, we provisioned an AgentCore Memory resource in your AWS environment by sending a `CreateMemoryRequest` to Bedrock. The request specifies which built-in strategies to enable, how memories should be organized via namespaces, a name for the resource, and how long raw events are retained (`eventExpiryDuration`). Each strategy is configured similarly — and notice that we don't specify any LLM or prompts here. AgentCore uses its default extraction and consolidation prompts and models out of the box. Custom strategies _do_ let you override the LLM and prompts, but for this workshop we're starting with the defaults.

Event creation against the AgentCore Memory resource looks like this:

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

A single-strategy memory resource (semantic only) looks like:

```java
public static CreateMemoryResponse createMemory() {
    BedrockAgentCoreControlClient controlClient = AWS.getBedrockAgentCoreControlClient();

    CreateMemoryRequest request = CreateMemoryRequest.builder()
        .name(MEMORY_ID)
        .description("This is an example that handles only Semantic Memories")
        .eventExpiryDuration(365)
        .memoryStrategies(MemoryStrategyInput.builder()
            .semanticMemoryStrategy(SemanticMemoryStrategyInput.builder()
                .name("Semantic")
                .description("Stores factual information and concepts")
                .namespaces(List.of("/strategies/{memoryStrategyId}/actors/{actorId}"))
            .build())
        .build())
    .build();

    return controlClient.createMemory(request);
}
```

The other built-in strategies follow the same pattern — swap `semanticMemoryStrategy` for `userPreferenceMemoryStrategy`, `episodicMemoryStrategy`, or `summaryMemoryStrategy` (the episodic strategy also takes a `reflectionConfiguration`, and episodic/summary use a session-scoped namespace `/strategies/{memoryStrategyId}/actors/{actorId}/sessions/{sessionId}`).

A custom strategy lets you override the extraction prompt:

```java
MemoryStrategyInput.builder()
    .customMemoryStrategy(CustomMemoryStrategyInput.builder()
        .name("Travel Facts")
        .configuration(CustomConfigurationInput.builder()
            .semanticOverride(SemanticOverrideConfigurationInput.builder()
                .extraction(SemanticOverrideExtractionConfigurationInput.builder()
                    .appendToPrompt("""
                        You are tasked with analyzing conversations to
                        extract the user's travel preferences...
                        """)
                .build())
            .build())
        .build())
    .description("Custom memory strategy for extracting travel preferences")
    .namespaces(List.of("/strategies/{memoryStrategyId}/actors/{actorId}"))
    .build())
.build()
```

## Other Options

AgentCore Memory is a great option because it takes care of most of the hardest parts for us — extraction, consolidation, storage, and retrieval are all handled by the managed service. But it's not the only choice in this space, and a few other tools take meaningfully different approaches.

### Mem0

[Mem0](https://github.com/mem0ai/mem0) is probably the most popular open-source memory layer at the moment, and the architecture will look very familiar after walking through AgentCore and our local implementation:

- A **memory extractor** module takes recent messages and uses an LLM to extract atomic facts/memories from the conversation.
- An **update phase** compares those raw memories against the N most similar existing memories, and an LLM decides what to do: `add`, `update`, `delete`, or no-op.

The Mem0 playground shows how memories are structured: personal details, category tags, user preferences, and professional details are all extracted from the conversation.

**The main differentiator is storage.** Mem0 uses a hybrid datastore approach:

- **Vectors** for semantic similarity.
- A **graph datastore** for tracking entity relationships.
- A **key-value store** for other structured facts.

The graph layer enables more complex relational queries between entities that we have facts about — something pure vector retrieval can't do well.

### LangMem

[LangMem](https://github.com/langchain-ai/langmem) is built by the LangChain team and supports three memory types:

- **Semantic** — facts and knowledge.
- **Episodic** — past interactions and events.
- **Procedural** — learned behaviors, rules, and instructions. Notably, procedural memory is stored as updated instructions in the agent's prompt itself.

LangMem can operate in a couple of different modes. Where AgentCore is essentially standalone and extracts information in the background, LangMem can also be inserted into the **hot path** by exposing memory management tools directly to the agent. The LLM can then decide to extract and store memories during the conversation itself — this adds latency but gives much more control over memory behavior.

LangMem also offers a **background memory process** where a separate LLM reflects on the conversation to extract memories, similar to what we looked at with AgentCore Memory. Memories are organized in a namespace hierarchy (folders by user, session, and type), again similar to AgentCore.

Storage is much less opinionated than AgentCore — most vector databases, traditional databases like Postgres, and key-value stores like Redis are all supported.

LangMem also attempts to improve retrieval by tracking how often memories are accessed and how recently they were used, adding metadata around **memory importance** or **memory strength**. (This is something we could absolutely add to our Qdrant local memory approach as additional metadata if we wanted to.)

### Zep

[Zep](https://github.com/getzep/zep) takes a much different approach. It's a fully graph-based solution built on a knowledge graph called [Graphiti](https://github.com/getzep/graphiti), and the key innovation is that Graphiti is a **time-based** knowledge graph.

It maintains structured graph data between entities while also preserving the **historical relationships** between them. That means Zep is aware of:

- When data entered the system.
- What the fact was at that time.
- How relationships change as things evolve — facts can be invalidated over time, and relationships can be updated as the world changes.

Zep also distinguishes between when an event **actually happened** vs. when the system **ingested it** — more specific than just stamping a record with `created_at`.

For comparison, our AgentCore Memory setup doesn't really handle this well: a user might tell the agent something happened a month ago, but the memory record will still be dated today. Similarly, when the model updates a memory, we typically don't preserve the previous value. AgentCore also has no real way to do the kind of graph traversal needed to determine relationships between entities — vector similarity alone can't do that.

The other major difference is that Zep is **not summary-based**. It keeps two distinct subgraphs:

- An **episodic subgraph** storing the raw conversation.
- A **semantic subgraph** storing entities and relationships derived from those conversations.

This means episodic memories stay **fully intact** — Zep is non-lossy, unlike summarization or fact-extraction-only approaches. The raw conversations are then bidirectionally linked to the semantic graph nodes they relate to, so you can always trace a derived fact back to the conversation it came from.

## Production Considerations

### Privacy and Data Lifecycle

Our implementation sets `eventExpiryDuration(30)`. Raw events expire after 30 days. But extracted long-term memories persist indefinitely. This creates important questions for production systems:

- What data is being extracted?
  - The AgentCore extraction prompts process USER and ASSISTANT messages, extracting facts and preferences.
  - The consolidation prompts are designed to skip PII and harmful content, but this is LLM-based filtering and is not guaranteed.
- How long should memories live?
  - Semantic facts ("lives in Austin") may be valid for years.
  - Preferences ("prefers dark mode") can change.
  - Episodic memories of specific interactions may become irrelevant.

- User consent and right to deletion. AgentCore provides `deleteMemory` for removing entire memory resources, but granular record-level deletion of specific memories may be needed for compliance.

### Memory Conflicts and Staleness

What happens when long-term memory says "favorite color is blue" but the user just said "actually it's green"? The consolidation logic handles this through UPDATE and DELETE operations. This processing is asynchronous and takes about a minute. During that window, the agent may have stale information in its retrieved memories that contradicts the current conversation.

In practice, the LLM usually handles this well because the current conversation context takes precedence in the prompt. But it is worth being aware that there is no hard guarantee of this. The model treats all context equally, and a strongly worded memory record could occasionally override a casual correction in the current conversation.

One practical mitigation is how we inject retrieved memories into the prompt. Rather than inserting raw memory text alongside the conversation history, our implementation wraps each record in typed XML tags that correspond to its strategy.

For example:

```xml
<user-preference>User prefers TypeScript over Java</user-preference>
<semantic>User is a software engineer based in Austin</semantic>
```

These tags appear in the `{memoryRecords}` placeholder in the thought prompt, which is a **separate section** from `{previousSteps}` (the live conversation history). This structural separation gives the LLM a clear signal about the provenance of each piece of information.

### Cost Implications

Memory adds cost at two points:

- **Persistence:** Every `persistMemoryActivity` call sends events to AgentCore, which triggers LLM-based extraction and embedding generation. At high message volume, this adds up.

- **Retrieval:** Every `retrieveMemoryRecordsActivity` call performs an embedding of the query and a vector search. This happens at the start of every THINKING step.

For cost optimization, consider batching persistence, taking everything from the USER_MESSAGE up through the ANSWER at once, limiting retrieval frequency by only retrieving on the first thinking step of each user message rather than every ReAct iteration, and using cheaper models for extraction where possible.

### Memory in Multi-Agent Systems

Connecting to Exercise 8: when multiple agents collaborate, memory architecture introduces new questions.

- Should agents share memory? A primary agent and a book-recommendation sub-agent might benefit from sharing user preference memory, but keeping their operational memory separate.
- Should the primary agent's memory include sub-agent interactions? If a sub-agent learned something about the user's preferences, should that be persisted in the primary agent's memory so it is available in future sessions?
- Memory as a coordination mechanism. Agents could communicate asynchronously through shared memory -- one agent writes findings, another reads them later. This is an alternative to the Signal-based approach from Exercise 8.

## ReAct Memory Integration Summary

In the context of a ReAct agent, the complete memory-augmented flow is:

1. **Initial Query:** User input is received by the ReAct Agent.
2. **Pre-Retrieval (RAG):** The current query and the STM (recent context) are used to query the LTM (Vector Database, Structured Memory, or Graph Memory). Hybrid search is crucial here.
3. **Context Augmentation:** The most relevant retrieved long-term memories are combined with the short-term conversation history to augment the LLM's prompt.
4. **ReAct Loop Execution:** The LLM proceeds with the Reasoning and Acting steps, using the augmented context.
5. **Post-Action Update (Temporal Activity):** Once the interaction segment is complete (or after a tool call), a Temporal Activity is triggered to extract salient facts from the full interaction, process them, and store/update the LTM (via ADD/UPDATE/DELETE operations). This process keeps the LLM's core context window lean while asynchronously preserving knowledge.

By externalizing memory using semantic vector databases, leveraging specialized storage for structured facts, and implementing dynamic LLM-driven consolidation mechanisms, you move beyond mere compression to a truly scalable system capable of retaining context over infinite conversations.

A simple way to think about the evolution is moving from storing a compressed narrative (a string summary) to storing discrete, structured thoughts (vectors, entities, principles) that can be instantly searched and recombined based on meaning -- much like consulting a specialized, meticulously indexed library rather than rereading a massive, single book summary.

## Sample Code

https://github.com/awslabs/amazon-bedrock-agentcore-samples/tree/main/01-tutorials/04-AgentCore-memory/02-long-term-memory

---

## Reference: AgentCore Strategy Prompts

The following sections document the actual system prompts used by AgentCore Memory for each strategy. These are useful for understanding what gets extracted and how consolidation works, and for designing custom strategy overrides.

### System prompt for semantic memory strategy

```plain
You are a long-term memory extraction agent supporting a lifelong learning system. Your task is to identify and extract meaningful information about the users from a given list of messages.

Analyze the conversation and extract structured information about the user according to the schema below. Only include details that are explicitly stated or can be logically inferred from the conversation.

- Extract information ONLY from the user messages. You should use assistant messages only as supporting context.
- If the conversation contains no relevant or noteworthy information, return an empty list.
- Do NOT extract anything from prior conversation history, even if provided. Use it solely for context.
- Do NOT incorporate external knowledge.
- Avoid duplicate extractions.

IMPORTANT: Maintain the original language of the user's conversation. If the user communicates in a specific language, extract and format the extracted information in that same language.
```

#### Extraction output schema

```xml
Your output must be a single JSON object, which is a list of JSON dicts following the schema. Do not provide any preamble or any explanatory text.

<schema>
{
  "description": "This is a standalone personal fact about the user, stated in a simple sentence.\nIt should represent a piece of personal information, such as life events, personal experience, and preferences related to the user.\nMake sure you include relevant details such as specific numbers, locations, or dates, if presented.\nMinimize the coreference across the facts, e.g., replace pronouns with actual entities.",
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

#### Semantic memory consolidation instructions

```md
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

## Decision Guidelines

### AddMemory (New Information)

Add only when the retrieved fact introduces entirely new information not covered by existing memories.

**Example**:

- Existing Memory: `[{"id": "0", "text": "User is a software engineer"}]`
- Retrieved Fact: `["Name is John"]`
- Action: AddMemory with new ID

### UpdateMemory (Preserve + Extend)

Preserve existing information while adding new details. Combine information coherently without losing specificity or changing meaning.

**Critical Rules for UpdateMemory**:

- **Preserve timestamps and specific details** from the original memory
- **Maintain semantic accuracy** - don't generalize or change the meaning
- Only enhance when new information genuinely adds value without contradiction
- Only enhance when new information is **closely relevant** to existing memories
- Attend to novel information that deviates from existing memories and expectations
- Consolidate and compress redundant memories to maintain information-density; strengthen based on reliability and recency; maximize SNR by avoiding idle words

**Example**:

- Existing: `[{"id": "1", "text": "Caroline attended an LGBTQ support group meeting that she found emotionally powerful."}]`
- Retrieved: `["Caroline found the support group very helpful"]`
- Action: UpdateMemory to `"Caroline attended an LGBTQ support group meeting that she found emotionally powerful and very helpful."`

**When NOT to update**:

- Information is essentially the same: "likes pizza" vs "loves pizza"
- Updating would change the fundamental meaning
- New fact contradicts existing information (use AddMemory instead)
- New fact contains new events with timestamps that differ from existing facts. Since enhanced memories share timestamps with original facts, this would create temporal contradictions. Use AddMemory instead.

### SkipMemory (No Change)

Use when information already exists in sufficient detail or when new information doesn't add meaningful value.

## Key Principles

- Conservation First: Preserve all specific details, timestamps, and context
- Semantic Preservation: Never change the core meaning of existing memories
- Coherent Integration: Lets enhanced memories read naturally and logically
```

#### Semantic memory consolidation output schema

```md
## Response Format

Return only this JSON structure, using double quotes for all keys and string values:
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
```

### System prompt for user preference memory strategy

```md
You are tasked with analyzing conversations to extract the user's preferences. You'll be analyzing two sets of data:

<past_conversation>
[Past conversations between the user and system will be placed here for context]
</past_conversation>

<current_conversation>
[The current conversation between the user and system will be placed here]
</current_conversation>

Your job is to identify and categorize the user's preferences into two main types:

- Explicit preferences: Directly stated preferences by the user.
- Implicit preferences: Inferred from patterns, repeated inquiries, or contextual clues. Take a close look at user's request for implicit preferences.

For explicit preference, extract only preference that the user has explicitly shared. Do not infer user's preference.

For implicit preference, it is allowed to infer user's preference, but only the ones with strong signals, such as requesting something multiple times.
```

```md
Extract all preferences and return them as a JSON list where each item contains:

1. "context": The background and reason why this preference is extracted.
2. "preference": The specific preference information
3. "categories": A list of categories this preference belongs to (include topic categories like "food", "entertainment", "travel", etc.)

For example:

[
{
"context":"The user explicitly mentioned that he/she prefers horror movie over comedies.",
"preference": "Prefers horror movies over comedies",
"categories": ["entertainment", "movies"]
},
{
"context":"The user has repeatedly asked for Italian restaurant recommendations. This could be a strong signal that the user enjoys Italian food.",
"preference": "Likely enjoys Italian cuisine",
"categories": ["food", "cuisine"]
}
]

Extract preferences only from <current_conversation>. Extract preference ONLY from the user messages. You should use assistant messages only as supporting context. Only extract user preferences with high confidence.

Maintain the original language of the user's conversation. If the user communicates in a specific language, extract and format the extracted information in that same language.

Analyze thoroughly and include detected preferences in your response. Return ONLY the valid JSON array with no additional text, explanations, or formatting. If there is nothing to extract, simply return empty list.
```

#### User preference consolidation instructions

```md
# ROLE

You are a Memory Manager that evaluates new memories against existing stored memories to determine the appropriate operation.

# INPUT

You will receive:

1. A list of new memories to evaluate
2. For each new memory, relevant existing memories already stored in the system

# TASK

You will be given a list of new memories and relevant existing memories. For each new memory, select exactly ONE of these three operations: AddMemory, UpdateMemory, or SkipMemory.

# OPERATIONS

1. AddMemory

Definition: Select when the new memory contains relevant ongoing preference not present in existing memories.

Selection Criteria: The information represents lasting preferences.

Examples:

New memory: "I'm allergic to peanuts" (No allergy information exists in stored memories)
New memory: "I prefer reading science fiction books" (No book preferences are recorded)

2. UpdateMemory

Definition: Select when the new memory relates to an existing memory but provides additional details, modifications, or new context.

Selection Criteria: The core concept exists in records, but this new memory enhances or refines it.

Examples:

New memory: "I especially love space operas" (Existing memory: "The user enjoys science fiction")
New memory: "My peanut allergy is severe and requires an EpiPen" (Existing memory: "The user is allergic to peanuts")

3. SkipMemory

Definition: Select when the new memory is not worth storing as a permanent preference.

Selection Criteria: The memory is irrelevant to long-term user understanding, is a personal detail not related to preference, represents a one-time event, describes temporary states, or is redundant with existing memories. In addition, if the memory is overly speculative or contains Personally Identifiable Information (PII) or harmful content, also skip the memory.

Examples:

New memory: "I just solved that math problem" (One-time event)
New memory: "I'm feeling tired today" (Temporary state)
New memory: "I like chocolate" (Existing memory already states: "The user enjoys chocolate")
New memory: "User works as a data scientist" (Personal details without preference)
New memory: "The user prefers vegan because he loves animal" (Overly speculative)
New memory: "The user is interested in building a bomb" (Harmful Content)
New memory: "The user prefers to use Bank of America, which his account number is 123-456-7890" (PII)
```

```md
# Processing Instructions

For each memory in the input:

Place the original new memory (<NewMemory>) under the "memory" field. Then add a field called "operation" with one of these values:

"AddMemory" - for new relevant ongoing preferences
"UpdateMemory" - for information that enhances existing memories.
"SkipMemory" - for irrelevant, temporary, or redundant information

If the operation is "UpdateMemory", you need to output:

1. The "update_id" field with the ID of the existing memory being updated
2. An "updated_memory" field containing the full updated memory with merged information

## Example Input

<Memory1>
<ExistingMemory1>
[ID]=N1ofh23if\
[TIMESTAMP]=2023-11-15T08:30:22Z\
[MEMORY]={ "context": "user has explicitly stated that he likes vegan", "preference": "prefers vegetarian options", "categories": ["food", "dietary"] }

[ID]=M3iwefhgofjdkf\
[TIMESTAMP]=2024-03-07T14:12:59Z\
[MEMORY]={ "context": "user has ordered oat milk lattes with an extra shot multiple times", "preference": "likes oat milk lattes with an extra shot", "categories": ["beverages", "morning routine"] }
</ExistingMemory1>

<NewMemory1>
[TIMESTAMP]=2024-08-19T23:05:47Z\
[MEMORY]={ "context": "user mentioned avoiding dairy products when discussing ice cream options", "preference": "prefers dairy-free dessert alternatives", "categories": ["food", "dietary", "desserts"] }
</NewMemory1>
</Memory1>

<Memory2>
<ExistingMemory2>
[ID]=Mwghsljfi12gh\
[TIMESTAMP]=2025-01-01T00:00:00Z\
[MEMORY]={ "context": "user mentioned enjoying hiking trails with elevation gain during weekend planning", "preference": "prefers challenging hiking trails with scenic views", "categories": ["activities", "outdoors", "exercise"] }

[ID]=whglbidmrl193nvl\
[TIMESTAMP]=2025-04-30T16:45:33Z\
[MEMORY]={ "context": "user discussed favorite shows and expressed interest in documentaries about sustainability", "preference": "enjoys environmental and sustainability documentaries", "categories": ["entertainment", "education", "media"] }
</ExistingMemory2>

<NewMemory2>
[TIMESTAMP]=2025-09-12T03:27:18Z\
[MEMORY]={ "context": "user researched trips to coastal destinations with public transportation options", "preference": "prefers car-free travel to seaside locations", "categories": ["travel", "transportation", "vacation"] }
</NewMemory2>
</Memory2>

<Memory3>
<ExistingMemory3>
[ID]=P4df67gh\
[TIMESTAMP]=2026-02-28T11:11:11Z\
[MEMORY]={ "context": "user has mentioned enjoying coffee with breakfast multiple times", "preference": "prefers starting the day with coffee", "categories": ["beverages", "morning routine"] }

[ID]=Q8jk12lm\
[TIMESTAMP]=2026-07-04T19:45:01Z\
[MEMORY]={ "context": "user has stated they typically wake up around 6:30am on weekdays", "preference": "has an early morning schedule on workdays", "categories": ["schedule", "habits"] }
</ExistingMemory3>

<NewMemory3>
[TIMESTAMP]=2026-12-25T22:30:59Z\
[MEMORY]={ "context": "user mentioned they didn't sleep well last night and felt tired today", "preference": "feeling tired and groggy", "categories": ["sleep", "wellness"] }
</NewMemory3>
</Memory3>

## Example Output

[{
"memory":{
"context": "user mentioned avoiding dairy products when discussing ice cream options",
"preference": "prefers dairy-free dessert alternatives",
"categories": ["food", "dietary", "desserts"]
},
"operation": "UpdateMemory",
"update_id": "N1ofh23if",
"updated_memory": {
"context": "user has explicitly stated that he likes vegan and mentioned avoiding dairy products when discussing ice cream options",
"preference": "prefers vegetarian options and dairy-free dessert alternatives",
"categories": ["food", "dietary", "desserts"]
}
},
{
"memory":{
"context": "user researched trips to coastal destinations with public transportation options",
"preference": "prefers car-free travel to seaside locations",
"categories": ["travel", "transportation", "vacation"]
},
"operation": "AddMemory",
},
{
"memory":{
"context": "user mentioned they didn't sleep well last night and felt tired today",
"preference": "feeling tired and groggy",
"categories": ["sleep", "wellness"]
},
"operation": "SkipMemory",
}]

Like the example, return only the list of JSON with corresponding operation. Do NOT add any explanation.
```

### System prompt for summary strategy

```md
You are a summary generator. You will be given a text block, a concise global summary, and a detailed summary you previous generated.
<task>

- Given the contexts(e.g. global summary, detailed previous summary), your goal is to generate
  (1) a concise global summary keeping in main target of the conversation, such as the task and the requirements.
  (2) a detailed delta summary of the given text block, without repeating the historical detailed summary.
- The previous summary is a context for you to understand the main topics.
- You should only output the delta summary, not the whole summary.
- The generated delta summary should be as concise as possible.
  </task>
  <extra_task_requirements>
- Summarize with the same language as the given text block. - If the messages are in a specific language, summarize with the same language.
  </extra_task_requirements>

When you generate global summary you ALWAYS follow the below guidelines:
<guidelines_for_global_summary>

- The global summary should be concise and to the point, only keep the most important information such as the task and the requirements.
- If there is no new high-level information, do not change the global summary. If there is new tasks or requirements, update the global summary.
- The global summary will be pure text wrapped by <global_summary></global_summary> tag.
- The global summary should be no exceed specified word count limit.
- Tracking the size of the global summary by calculating the number of words. If the word count reaches the limit, try to compress the global summary.
  </guidelines_for_global_summary>

When you generate detailed delta summaries you ALWAYS follow the below guidelines:
<guidelines_for_delta_summary>

- Each summary MUST be formatted in XML format.
- You should cover all important topics.
- The summary of the topic should be place between <topic name="$TOPIC_NAME"></topic>.
- Only include information that are explicitly stated or can be logically inferred from the conversation.
- Consider the timestamps when you synthesize the summary.
- NEVER start with phrases like 'Here's the summary...', provide directly the summary in the format described below.
  </guidelines_for_delta_summary>

The XML format of each summary is as it follows:

<existing_global_summary_word_count>
$Word Count
</existing_global_summary_word_count>

<global_summary_condense_decision>
The total word count of the existing global summary is $Total Word Count.
The word count limit for global summary is $Word Count Limit.
Since we exceed/do not exceed the word count limit, I need to condense the existing global summary/I don't need to condense the existing global summary.
</global_summary_condense_decision>

<global_summary>
...
</global_summary>

<delta_detailed_summary>
<topic name="$TOPIC_NAME">
...
</topic>
...
</delta_detailed_summary>
```
