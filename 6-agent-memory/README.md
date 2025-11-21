# Exercise 6 - Agent Memory

When working with AI Agents, especially with Temporal, we can design agents that can potentially run for extended periods of time, even indefinitely. This capability opens up exciting possibilities for creating agents that can remember past interactions, learn from them, and adapt their behavior over time.

## Goals

## What you need to know

- Hierarchical Memory
- Dynamic Memory Management
- External Persistence

### Short-Term Memory Architecture

Short-term memory (STM) allows the agent to maintain continuity throughout a single interaction, tracking recent prompts, tool outputs, and conversation history. This is analogous to the current context window used by the LLM during the ReAct loop.

Use a low-latency persistent store (like RedisSaver or an in-memory dictionary) for checkpointing the current session state. In a Temporal Workflow, this would ensure that the immediate conversation history is available quickly and checkpointed reliably. Limit the sheer volume of raw conversation in the immediate context. Use the STM to hold only the most recent interactions (e.g., the last 5-10 turns).

Implement Time-to-Live (TTLs) for short-lived or ephemeral memory items, such as temporary itineraries or session transcripts, allowing them to expire automatically when no longer relevant. RedisSaver or in-memory dictionaries can support this.

### Long-Term Memory (LTM) Architecture

Long-term memory helps the agent recall context across different sessions or tasks, such as user preferences, historical behavior, or summaries of past conversations. The shift from your current simple compression to a structured LTM involves incorporating multiple abstraction layers.

Core LTM Foundation: Retrieval-Augmented Generation (RAG). The foundation of scalable LTM is the use of a Vector Database (e.g., Redis vector database) to store embeddings of prior interactions.

1. Store Discrete Units: Instead of saving an entire summarized session, break down memory into discrete units such as individual interactions, LLM responses, or key facts extracted from the conversation
2. Vectorization: Embed these discrete units into high-dimensional vectors. When the agent receives a new query, the query is also vectorized, and the system searches the database for semantically similar stored memories, even if the exact words differ
3. Hybrid Retrieval: Use sophisticated hybrid search techniques, combining semantic similarity search (via vectors) with metadata filtering (via tags). For example, you can tag embeddings with the user_id, task_type ("booking" or "support"), and timestamps. This allows the agent to recall the most relevant memories, filtering out history that is too old or belongs to a different context.

### Specialized Memory Tiers

For infinitely long conversations, modern agent architectures employ multiple, specialized memory types beyond simple interaction history.

Graph Memory (Mem0g)

Captures complex relational structures between conversational elements (entities as nodes, relationships as edges). Excellent for multi-hop reasoning and temporal queries.
Uses Neo4j or similar graph database to model facts like: (User, lives_in, Austin)

### Dynamic Management and Update Mechanisms

The biggest architectural improvement over simple summarization is replacing periodic compression with dynamic, intelligent memory management that can evolve over time. . You can integrate these processes using Temporal Activities or separate background services.

1. Dynamic Extraction (Mem0 Model): Instead of summarizing everything, use the LLM to dynamically extract, evaluate, and consolidate salient information from ongoing conversations
2. LLM-Driven Updates: Implement a robust update phase (potentially triggered as an asynchronous Temporal Activity) where the LLM uses a function-calling interface to determine the fate of new memories

   ◦ ADD: Create a new memory if no semantically similar memory exists.
   ◦ UPDATE: Augment existing memories with complementary, richer information.
   ◦ DELETE: Remove memories that are contradicted by new information, ensuring temporal consistency
   ◦ NOOP: Ignore facts that are already present or irrelevant

3. Self-Adaptive Reorganization (EVOLVE-MEM): The EVOLVE-MEM architecture utilizes a Self-Improvement Engine that continuously monitors performance (accuracy, retrieval latency, coverage) and automatically triggers memory reorganization, such as dynamic clustering or parameter tuning, when thresholds are exceeded. This ensures the memory structure remains relevant as the agent's experience grows

### Summary of ReAct Memory Integration

In the context of a ReAct agent, the improved flow would be:

1. Initial Query: User input is received by the ReAct Agent.
2. Pre-Retrieval (RAG): The current query and the STM (recent context) are used to query the LTM (Vector Database, Structured Memory, or Graph Memory). Hybrid search is crucial here
3. Context Augmentation: The most relevant retrieved long-term memories are combined with the short-term conversation history to augment the LLM's prompt
4. ReAct Loop Execution: The LLM proceeds with the Reasoning and Acting steps, using the augmented context
5. Post-Action Update (Temporal Activity): Once the interaction segment is complete (or after a tool call), a Temporal Activity is triggered to extract salient facts from the full interaction, process them, and store/update the LTM (via ADD/UPDATE/DELETE operations). This process keeps the LLM's core context window lean while asynchronously preserving knowledge.

By externalizing memory using semantic vector databases, leveraging specialized storage for structured facts, and implementing dynamic LLM-driven consolidation mechanisms, you move beyond mere compression to a truly scalable system capable of retaining context over infinite conversations

A simple way to think about the evolution of your system is moving from storing a compressed narrative (your current string summary) to storing discrete, structured thoughts (vectors, entities, principles) that can be instantly searched and recombined based on meaning, much like consulting a specialized, meticulously indexed library rather than rereading a massive, single book summary.

High-level design

Working memory (WM): small rolling window (e.g., last 12–20 turns) + the current scratchpad/tool traces. Used directly in prompts. Hard cap in tokens.

Episodic memory (EM): append-only chronological events (user/agent messages, tool outcomes, decisions) chunked and immutable. Think log segments with indices.

Semantic memory (SM): de-duplicated facts, entities, preferences, skills, constraints, and long-lived objectives extracted from EM, stored as structured records + embeddings.

Indexes: hybrid retrieval (BM25/Full-Text + vector). Relevance = α·similarity + β·recency + γ·importance.

Consolidation: background/cron Temporal Workflows that distill EM → SM, refresh embeddings, decay stale items, and maintain hierarchical summaries.

### AgentCore Memory Resource

Encapsulates both raw events (STM) and processed long-term memories (LTM).

- memoryId
  - A unique identifier used for each user or memory context
  - Mandatory for persisting and loading stored memory across different sessions for a specific user, enabling personalization
- actorId
  - Identifies the entity associated with the memory (e.g., user, agent, project)
  - Used with sessionId to enforce hierarchical namespaces and precise retrieval of relevant context
- sessionId
  - Groups related memory events together during a single interaction
  - Essential for tracking the chronological narrative flow within a short-term conversation
- Event (raw)

  - An immutable record of an individual interaction (user prompt, agent reply, tool output)
  - Constitutes the Short-Term Memory (STM). These are stored chronologically in the memory resource

- Session Summary Object
  - A durable, compressed, token-efficient distillation of an entire session, generated by an LLM upon session termination
  - Constitutes the primary artifact of Long-Term Memory (LTM), preserving context without exceeding the context window

AgentCore Memory can be used with any Agent solution, including completely custom Agents, using the AWS SDK for JavaScript/TypeScript or Java.

### Sample Code

https://github.com/awslabs/amazon-bedrock-agentcore-samples/tree/main/01-tutorials/04-AgentCore-memory/02-long-term-memory

### How it works
