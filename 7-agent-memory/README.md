# Exercise 7 - Agent Memory

## Why Memory Matters

Every LLM has a finite context window -- a hard limit on how much information it can "see" at once. Without memory management, an agent faces two inevitable outcomes as conversations grow: either it loses information when older context is discarded, or it hits token limits and fails entirely.

This constraint is the fundamental tension that memory architecture exists to solve. A well-designed memory system lets an agent maintain continuity within a conversation, recall relevant information across sessions, and operate indefinitely without degradation.

When working with AI Agents, especially with Temporal, we can design agents that can potentially run for extended periods of time, even indefinitely. This capability opens up exciting possibilities for creating agents that can remember past interactions, learn from them, and adapt their behavior over time.

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

This is the most important distinction in agent memory architecture. They are related but different:

**Working context** is what the agent can see right now -- the current conversation history, tool results, and any retrieved information that has been injected into the prompt. In our implementation, this is the `List<ContextEntry>` maintained by the workflow. It lives in memory, it is bounded by the context window size, and it exists only for the lifetime of the current workflow execution.

**Persistent memory** is what the agent remembers across sessions -- durable knowledge stored externally in a database, vector store, or managed service like AgentCore Memory. It survives workflow restarts, compaction, and even `continueAsNew`. The agent cannot see it directly; it must be explicitly retrieved and injected into the working context.

The bridge between them is **memory retrieval**: at the start of each thinking step, the agent queries its persistent memory, and the most relevant records are injected into the working context alongside the conversation history. This is the "Pre-Retrieval (RAG)" pattern that makes long-term memory useful.

### Short-Term Memory Architecture

Short-term memory (STM) allows the agent to maintain continuity throughout a single interaction, tracking recent prompts, tool outputs, and conversation history. This is the current context window used by the LLM during the ReAct loop.

In practice, short-term memory is the rolling window of recent interactions that the agent carries in its working context. Key design decisions include:

- **Window size.** How many recent turns to keep in the immediate context (e.g., the last 5-10 turns). Our implementation keeps all entries until compaction is triggered.
- **Checkpointing.** Using a low-latency persistent store for the current session state. In a Temporal Workflow, this happens automatically through event history -- the state is checkpointed reliably after every Activity completion.
- **Time-to-Live (TTL).** For ephemeral or session-scoped memory items, implementing TTLs allows them to expire automatically when no longer relevant.

### Long-Term Memory Architecture

Long-term memory (LTM) helps the agent recall context across different sessions or tasks, such as user preferences, historical behavior, or summaries of past conversations.

The foundation of scalable LTM is Retrieval-Augmented Generation (RAG) using a vector database to store embeddings of prior interactions:

1. **Store Discrete Units.** Instead of saving an entire summarized session, break down memory into discrete units such as individual interactions, LLM responses, or key facts extracted from the conversation.
2. **Vectorization.** Embed these discrete units into high-dimensional vectors. When the agent receives a new query, the query is also vectorized, and the system searches the database for semantically similar stored memories, even if the exact words differ.
3. **Hybrid Retrieval.** Use sophisticated hybrid search techniques, combining semantic similarity search (via vectors) with metadata filtering (via tags). For example, you can tag embeddings with the user_id, task_type ("booking" or "support"), and timestamps. This allows the agent to recall the most relevant memories, filtering out history that is too old or belongs to a different context.

### Compaction vs. Memory Persistence

These are complementary but distinct operations, and our codebase implements both.

**Compaction** (covered in Exercise 5) is about keeping the current session's working context within token limits. When the context grows too large, the agent summarizes it and discards the originals. Compaction is lossy by design -- it trades detail for space. After compaction, the specific wording of earlier messages is gone, replaced by a compressed summary.

**Memory persistence** is about extracting durable knowledge _before_ that information would be compacted away or lost. Rather than summarizing everything into a single blob, persistence uses AI to identify specific facts, preferences, and patterns worth remembering long-term, and stores them in a structured external system.

The ordering matters: our workflow persists to memory after each answer (while the full detail is still available), and compaction happens later (when `continueAsNew` triggers). This ensures that important information is extracted at full fidelity before compression reduces it.

### Memory Strategy Types

Modern memory systems use multiple specialized strategies. Understanding when to use each is important:

**Semantic memory** stores discrete factual knowledge -- "The user lives in Austin," "The user is a software engineer." These are stable facts that don't change often. Best for: personal information, stated facts, domain knowledge that the agent learns from interactions.

**User preference memory** captures behavioral patterns and choices -- "Prefers TypeScript over Java," "Likes outdoor dining." These are inferred from patterns across conversations. Best for: personalizing responses, anticipating needs, adapting tone and recommendations.

**Episodic memory** stores narrative sequences of what happened -- the temporal flow of events with context about what was tried, what worked, and what was learned. Best for: learning from past problem-solving attempts, understanding how previous interactions unfolded, building procedural knowledge.

**Summary memory** maintains compressed overviews of entire sessions -- global summaries of conversation topics and detailed delta summaries of specific discussion points. Best for: quickly re-establishing context from previous sessions without loading full history, providing high-level continuity.

A well-architected system uses multiple strategies simultaneously. Semantic facts and user preferences are queried based on relevance to the current conversation. Episodic memories provide deeper context for similar situations. Summaries offer broad continuity. The agent's memory retrieval step can query across all of these and inject the most relevant records into the working context.

### Specialized Memory Tiers

For infinitely long conversations, modern agent architectures employ additional specialized memory types beyond the strategies above.

**Graph Memory (Mem0g)** captures complex relational structures between conversational elements (entities as nodes, relationships as edges). Excellent for multi-hop reasoning and temporal queries. Uses Neo4j or similar graph database to model facts like: (User, lives_in, Austin).

### High-level Design for Production Systems

- **Working memory (WM):** small rolling window (e.g., last 12-20 turns) + the current scratchpad/tool traces. Used directly in prompts. Hard cap in tokens.
- **Episodic memory (EM):** append-only chronological events (user/agent messages, tool outcomes, decisions) chunked and immutable. Think log segments with indices.
- **Semantic memory (SM):** de-duplicated facts, entities, preferences, skills, constraints, and long-lived objectives extracted from EM, stored as structured records + embeddings.
- **Indexes:** hybrid retrieval (BM25/Full-Text + vector). Relevance = alpha * similarity + beta * recency + gamma * importance.
- **Consolidation:** background/cron Temporal Workflows that distill EM to SM, refresh embeddings, decay stale items, and maintain hierarchical summaries.

### Dynamic Memory Management

The biggest architectural improvement over simple summarization is replacing periodic compression with dynamic, intelligent memory management that can evolve over time. You can integrate these processes using Temporal Activities or separate background services.

1. **Dynamic Extraction (Mem0 Model):** Instead of summarizing everything, use the LLM to dynamically extract, evaluate, and consolidate salient information from ongoing conversations.

2. **LLM-Driven Updates:** Implement a robust update phase (potentially triggered as an asynchronous Temporal Activity) where the LLM uses a function-calling interface to determine the fate of new memories:
   - ADD: Create a new memory if no semantically similar memory exists.
   - UPDATE: Augment existing memories with complementary, richer information.
   - DELETE: Remove memories that are contradicted by new information, ensuring temporal consistency.
   - NOOP: Ignore facts that are already present or irrelevant.

3. **Self-Adaptive Reorganization (EVOLVE-MEM):** The EVOLVE-MEM architecture utilizes a Self-Improvement Engine that continuously monitors performance (accuracy, retrieval latency, coverage) and automatically triggers memory reorganization, such as dynamic clustering or parameter tuning, when thresholds are exceeded. This ensures the memory structure remains relevant as the agent's experience grows.

## How Memory Integrates with the Temporal Agent

Our Exercise 7 implementation extends the base ReAct workflow from Exercise 5 with two new Activities that bridge the working context and persistent memory.

### The Memory-Augmented ReAct Loop

The key change from Exercise 5 is what happens at the start of the THINKING step. Before the LLM reasons about anything, the workflow first queries long-term memory to augment the context:

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

In the workflow code, this looks like:

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

The `retrieveMemoryRecordsActivity` calls AgentCore Memory's semantic search API. It takes the current context as a search query and a strategy type to query against:

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

The retrieved records are wrapped in XML tags (e.g., `<user-preference>Favorite color is blue</user-preference>`) and injected into the thought prompt's `{memoryRecords}` placeholder. The LLM sees these alongside the conversation history and can use them in its reasoning.

The current implementation queries only USER_PREFERENCE strategy. The TODO exercise encourages experimenting with other strategies (episodic, semantic, summary) to see how different types of memory affect the agent's responses.

### Memory Persistence Activity

The `persistMemoryActivity` sends conversation entries to AgentCore Memory as events. After each answer, the workflow collects all entries from the most recent user message through the answer and persists them as a batch:

```java
public void persistMemoryActivity(List<ContextEntry> entries) {
    AgentCoreMemory.createEvent(entries);
}
```

Under the hood, each `ContextEntry` is converted to a `Conversational` payload with its XML string representation and role (USER or ASSISTANT). AgentCore Memory then asynchronously processes these events through its configured strategies -- extracting semantic facts, identifying user preferences, and generating session summaries. This processing typically takes about a minute and requires no additional code.

The `sessionId` for memory events is set to the Temporal workflow ID, which remains stable across `continueAsNew` calls (only the _run ID_ changes). This means a single long-running conversation keeps the same memory session even through `continueAsNew` boundaries, and the extracted long-term memories persist across sessions under the same `actorId`.

### The Cold Start Pattern

One of the most compelling demonstrations of long-term memory is the cold start scenario. When the agent starts a brand new conversation (new workflow execution, empty `List<ContextEntry>`), it has zero conversation history. But if the user has interacted with the agent before, the `retrieveMemoryRecordsActivity` call at the start of the first THINKING step returns relevant memories from past sessions.

The exercise TODO demonstrates this:

1. Start a conversation and tell the agent your preferences (favorite color, coffee, etc.)
2. Wait a few minutes for AgentCore to process the events into long-term memories
3. Start a completely new conversation and ask "what do you know about me?"
4. The agent has no conversation history but can still answer from retrieved LTM records

This is what transforms an agent from a stateless tool into something that feels like it has a relationship with the user over time.

### Token Budget Allocation

When building the prompt for the thought activity, you are allocating a fixed token budget across multiple sources:

- **System instructions and tool definitions** (fixed cost, typically 500-2000 tokens)
- **Retrieved memory records** (variable, controlled by `maxResults` -- our implementation limits to 4 records)
- **Conversation history** (variable, grows with each turn)
- **Reserve for the model's response** (must leave room for output)

The implementation uses `ModelUtils.truncateContextToTokenLimit` to ensure the conversation history fits, and limits memory retrieval to 4 results. In a production system, you would want to be more deliberate about this allocation -- perhaps reserving a fixed token budget for each source and dynamically adjusting based on what is available and relevant.

## AWS Bedrock AgentCore Memory

AgentCore Memory collects memory events during agent interactions and processes them into structured long-term memories using different configurable strategies. These strategies define how to extract and store important information, organizing them by namespaces based on actorId and sessionId. When developing with AgentCore Memory the process is mostly automatic. After the events are collected, the memory processing pipeline analyzes the conversations, extracts relevant facts and summaries using AI models, and stores them in a structured way.

The Agent, when building up its next context, can query the long-term memory using the actorId and sessionId to retrieve relevant memories. This allows the agent to maintain context across sessions and provide more personalized responses without needing to manage complex memory infrastructure manually.

AgentCore Memory can be used with any Agent solution, including completely custom Agents, using the AWS SDK for JavaScript/TypeScript or Java.

### AgentCore Memory Resource

The memory resource is the central container. It encapsulates both raw events (STM) and processed long-term memories (LTM).

- **memoryId** -- A unique identifier for the memory resource. Required for all read and write operations against AgentCore Memory.
- **actorId** -- Identifies the entity associated with the memory (e.g., user, agent, project). Used with sessionId to enforce hierarchical namespaces and precise retrieval of relevant context.
- **sessionId** -- Groups related memory events together during a single interaction. Essential for tracking the chronological narrative flow within a short-term conversation. In our implementation, this maps to the Temporal workflow ID.
- **Event (raw)** -- An immutable record of an individual interaction (user prompt, agent reply, tool output). Constitutes the Short-Term Memory. These are stored chronologically in the memory resource.
- **Session Summary Object** -- A durable, compressed, token-efficient distillation of an entire session, generated by an LLM upon session termination. Constitutes the primary artifact of Long-Term Memory, preserving context without exceeding the context window.

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

Bedrock AgentCore also offers Custom memory strategies that let you choose a specific LLM and override the prompt for extraction and consolidation to your specific domain or use case. For example, you might want to append to the semantic memory prompt so that it only extracts specific types of facts or memories.

Custom strategy documentation: https://docs.aws.amazon.com/bedrock-agentcore/latest/devguide/memory-self-managed-strategies.html#use-self-managed-strategy

### Processing Pipeline

Long-term memory operates through Memory Strategies that define what information to extract and how to process it. The system works automatically in the background:

1. **Conversation Analysis:** Saved conversations are analyzed based on configured strategies
2. **Information Extraction:** Important data (facts, preferences, summaries) is extracted using AI models
3. **Structured Storage:** Extracted information is organized in namespaces for efficient retrieval
4. **Semantic Indexing:** Information is vectorized for natural language search capabilities
5. **Consolidation:** Similar information is merged and refined over time

Processing Time: Typically takes ~1 minute after conversations are saved, with no additional code required.

Behind the scenes, the pipeline uses AI-powered extraction with foundation models, creates vector embeddings for similarity-based retrieval, structures information using configurable path-like hierarchies, automatically consolidates similar information to prevent duplication, and continuously improves extraction quality based on conversation patterns.

Important: For semantic and user preference memory strategies, only USER and ASSISTANT role messages are processed for long-term memory extraction. Messages with other role types are skipped. For the summary strategy, all roles are processed.

## Production Considerations

### Privacy and Data Lifecycle

Our implementation sets `eventExpiryDuration(30)` -- raw events expire after 30 days. But extracted long-term memories persist indefinitely. This creates important questions for production systems:

- What data is being extracted? The AgentCore extraction prompts process USER and ASSISTANT messages, extracting facts and preferences. The consolidation prompts are designed to skip PII and harmful content, but this is LLM-based filtering -- not guaranteed.
- How long should memories live? Semantic facts ("lives in Austin") may be valid for years. Preferences ("prefers dark mode") can change. Episodic memories of specific interactions may become irrelevant.
- User consent and right to deletion. AgentCore provides `deleteMemory` for removing entire memory resources, but granular record-level deletion of specific memories may be needed for compliance.

### Memory Conflicts and Staleness

What happens when long-term memory says "favorite color is blue" but the user just said "actually it's green"? The consolidation logic handles this through UPDATE and DELETE operations -- but this processing is asynchronous (about 1 minute). During that window, the agent may have stale information in its retrieved memories that contradicts the current conversation.

In practice, the LLM usually handles this well because the current conversation context takes precedence in the prompt. But it is worth being aware that there is no hard guarantee -- the model treats all context equally, and a strongly worded memory record could occasionally override a casual correction in the current conversation.

One practical mitigation is how we inject retrieved memories into the prompt. Rather than inserting raw memory text alongside the conversation history, our implementation wraps each record in typed XML tags that correspond to its strategy -- for example:

```xml
<user-preference>User prefers TypeScript over Java</user-preference>
<semantic>User is a software engineer based in Austin</semantic>
```

These tags appear in the `{memoryRecords}` placeholder in the thought prompt, which is a **separate section** from `{previousSteps}` (the live conversation history). This structural separation gives the LLM a clear signal about the provenance of each piece of information.

### Cost Implications

Memory adds cost at two points:

- **Persistence:** Every `persistMemoryActivity` call sends events to AgentCore, which triggers LLM-based extraction and embedding generation. At high message volume, this adds up.
- **Retrieval:** Every `retrieveMemoryRecordsActivity` call performs an embedding of the query and a vector search. This happens at the start of every THINKING step.

For cost optimization, consider: batching persistence (our implementation already does this -- it persists from USER_MESSAGE through ANSWER as a batch), limiting retrieval frequency (perhaps only on the first thinking step of each user message rather than every ReAct iteration), and using cheaper models for extraction where possible.

### Memory in Multi-Agent Systems

Connecting to Exercise 8: when multiple agents collaborate, memory architecture introduces new questions.

- Should agents share memory? A primary agent and a book-recommendation sub-agent might benefit from sharing user preference memory, but keeping their operational memory separate.
- Should the primary agent's memory include sub-agent interactions? If the book agent found that the user likes sailing books, should that be persisted in the primary agent's memory so it is available in future sessions?
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

```
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

```plain
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

```plain
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

```plain

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

```plain

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

```plain

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

```plain

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

```plain

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
- Summarize with the same language as the given text block.
    - If the messages are in a specific language, summarize with the same language.
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
