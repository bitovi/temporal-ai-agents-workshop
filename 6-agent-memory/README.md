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

Long-term memory operates through Memory Strategies that define what information to extract and how to process it. The system works automatically in the background:

#### Processing Pipeline

- Conversation Analysis: Saved conversations are analyzed based on configured strategies
- Information Extraction: Important data (facts, preferences, summaries) is extracted using AI models
- Structured Storage: Extracted information is organized in namespaces for efficient retrieval
- Semantic Indexing: Information is vectorized for natural language search capabilities
- Consolidation: Similar information is merged and refined over time

Processing Time: Typically takes ~1 minute after conversations are saved, with no additional code required.

#### Behind the Scenes

- AI-Powered Extraction: Uses foundation models to understand and extract relevant information
- Vector Embeddings: Creates semantic representations for similarity-based retrieval
- Namespace Organization: Structures information using configurable path-like hierarchies
- Automatic Consolidation: Merges and refines similar information to prevent duplication
- Incremental Learning: Continuously improves extraction quality based on conversation patterns

```python
# defining Memory Strategies
strategies = [{
    "semanticMemoryStrategy": {
        "name": "semantic-facts",
        "namespaces": ["/customer/{actorId}/facts"],
    },
    "summaryMemoryStrategy": {
        "name": "conversation-summary",
        "namespaces": ["/customer/{actorId}/{sessionId}/summary"],
    },
    "userPreferenceMemoryStrategy": {
        "name": "user-preferences",
        "namespace": ["/customer/{actorId}/preferences"],
    }
]
```

Bedrock AgentCore also offers Custom memory strategies that lets you choose a specific LLM and override the prompt for extraction and consolidation to your specific domain or use case. For example, you might want to append to the semantic memory prompt so that it only extracts specific types of facts or memories.

### Conclusion

Amazon Bedrock AgentCore Memory provides a comprehensive solution to one of the most challenging aspects of building effective AI agents—maintaining context and learning from interactions. By combining flexible short-term event storage with intelligent long-term memory extraction using AgentCore Memory, you can create more personalized, contextual, and helpful AI experiences without managing complex memory infrastructure. The service’s hierarchical namespaces, customizable memory strategies, and advanced features provide the foundations for sophisticated agent behaviors that feel more natural and human-like.

### Mark's Explanation of AgentCore Memory

AgentCore Memory collects memory events during agent interactions and processes them into structured long-term memories using different configuratable strategies. These strategies define how to extract and store important information, organizing them by namespaces based on actorId and sessionId. When developing with AgentCore Memory the process is mostly automatic. After the events are collected, the memory processing pipeline analyzes the conversations, extracts relevant facts and summaries using AI models, and stores them in a structured way.

The Agent, when building up its next context, can query the long-term memory using the actorId and sessionId to retrieve relevant memories. This allows the agent to maintain context across sessions and provide more personalized responses without needing to manage complex memory infrastructure manually.

### Custom Strategies

https://docs.aws.amazon.com/bedrock-agentcore/latest/devguide/memory-self-managed-strategies.html#use-self-managed-strategy

### Testing Custom Strategies

```plain
aws bedrock-agentcore create-event \
  --memory-id "your-memory-id" \
  --actor-id "test-user" \
  --session-id "test-session-1" \
  --event-timestamp "2024-01-15T10:00:00Z" \
  --payload '[{
    "conversational": {
      "content": {"text": "I prefer Italian restaurants with outdoor seating"},
      "role": "USER"
    }
  }]'
```

```shell
# List records by namespace
aws bedrock-agentcore list-memory-records \
  --memory-id "your-memory-id" \
  --namespace "/" # lists all records that match the namespace prefix
```

For semantic and user preference memory strategy, only USER and ASSISTANT role messages are processed for long term memory extraction and messages with rest of the role types are skipped. For summary strategy all roles are processed.


### System prompt for semantic memory strategy

https://docs.aws.amazon.com/bedrock-agentcore/latest/devguide/memory-system-prompt.html

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

#### Consolidation instructions

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

#### Consolidation output schema

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

#### Consolidation instructions

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
[ID]=N1ofh23if\\
[TIMESTAMP]=2023-11-15T08:30:22Z\\
[MEMORY]={ "context": "user has explicitly stated that he likes vegan", "preference": "prefers vegetarian options", "categories": ["food", "dietary"] }

[ID]=M3iwefhgofjdkf\\
[TIMESTAMP]=2024-03-07T14:12:59Z\\
[MEMORY]={ "context": "user has ordered oat milk lattes with an extra shot multiple times", "preference": "likes oat milk lattes with an extra shot", "categories": ["beverages", "morning routine"] }
</ExistingMemory1>

<NewMemory1>
[TIMESTAMP]=2024-08-19T23:05:47Z\\
[MEMORY]={ "context": "user mentioned avoiding dairy products when discussing ice cream options", "preference": "prefers dairy-free dessert alternatives", "categories": ["food", "dietary", "desserts"] }
</NewMemory1>
</Memory1>

<Memory2>
<ExistingMemory2>
[ID]=Mwghsljfi12gh\\
[TIMESTAMP]=2025-01-01T00:00:00Z\\
[MEMORY]={ "context": "user mentioned enjoying hiking trails with elevation gain during weekend planning", "preference": "prefers challenging hiking trails with scenic views", "categories": ["activities", "outdoors", "exercise"] }

[ID]=whglbidmrl193nvl\\
[TIMESTAMP]=2025-04-30T16:45:33Z\\
[MEMORY]={ "context": "user discussed favorite shows and expressed interest in documentaries about sustainability", "preference": "enjoys environmental and sustainability documentaries", "categories": ["entertainment", "education", "media"] }
</ExistingMemory2>

<NewMemory2>
[TIMESTAMP]=2025-09-12T03:27:18Z\\
[MEMORY]={ "context": "user researched trips to coastal destinations with public transportation options", "preference": "prefers car-free travel to seaside locations", "categories": ["travel", "transportation", "vacation"] }
</NewMemory2>
</Memory2>

<Memory3>
<ExistingMemory3>
[ID]=P4df67gh\\
[TIMESTAMP]=2026-02-28T11:11:11Z\\
[MEMORY]={ "context": "user has mentioned enjoying coffee with breakfast multiple times", "preference": "prefers starting the day with coffee", "categories": ["beverages", "morning routine"] }

[ID]=Q8jk12lm\\
[TIMESTAMP]=2026-07-04T19:45:01Z\\
[MEMORY]={ "context": "user has stated they typically wake up around 6:30am on weekdays", "preference": "has an early morning schedule on workdays", "categories": ["schedule", "habits"] }
</ExistingMemory3>

<NewMemory3>
[TIMESTAMP]=2026-12-25T22:30:59Z\\
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
