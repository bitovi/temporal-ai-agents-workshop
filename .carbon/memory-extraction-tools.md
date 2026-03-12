# Memory Extraction Tool Definitions

## User Preference Memory Tool Definition

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

## Semantic Memory Tool Definition

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
  },
  { "name": "RemoveDoc", "input": { "json_doc_id": "abc123" } }
]
```

## Episodic Memory Tool Definition

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

```json
{
  "name": "RemoveMemory",
  "input": {
    "memoryId": "c3d551fa097b5ec09ad37057950fb0b1"
  }
}
```
