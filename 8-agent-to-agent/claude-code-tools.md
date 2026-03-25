## Claude Code Tool Calls

```js
registry.register("Edit", {
  description: `Performs exact string replacements in files.

Usage:
- You must use the Read tool at least once before editing a file.
- The edit will FAIL if old_string is not unique in the file. Provide more surrounding context to make it unique, or use replace_all.
- Use replace_all for renaming variables or replacing all occurrences across the file.
- When old_string is empty and the file doesn't exist, creates a new file with new_string as content.`,
  input_schema: {
    type: "object",
    properties: {
      file_path: {
        type: "string",
        description: "The absolute path to the file to modify",
      },
      old_string: {
        type: "string",
        description:
          "The text to replace (must be unique in the file unless replace_all is true)",
      },
      new_string: {
        type: "string",
        description: "The replacement text (must be different from old_string)",
      },
      replace_all: {
        type: "boolean",
        description: "Replace all occurrences of old_string (default: false)",
        default: false,
      },
    },
    required: ["file_path", "old_string", "new_string"],
  },
});
```

```js
registry.register("Write", {
  description: "Write content to a file. Creates parent directories if needed.",
  input_schema: {
    type: "object",
    properties: {
      file_path: { type: "string", description: "Absolute path to write to" },
      content: { type: "string", description: "Content to write" },
    },
    required: ["file_path", "content"],
  },
});
```

```js
registry.register("Read", {
  description:
    "Read a file from the filesystem. Returns content with line numbers.",
  input_schema: {
    type: "object",
    properties: {
      file_path: { type: "string", description: "Absolute path to the file" },
      offset: {
        type: "number",
        description: "Line number to start from (1-indexed)",
      },
      limit: { type: "number", description: "Max lines to read" },
    },
    required: ["file_path"],
  },
});
```

```js
registry.register("Bash", {
  description:
    "Execute a bash command and return its output. Use for system commands that require shell execution.",
  input_schema: {
    type: "object",
    properties: {
      command: { type: "string", description: "The bash command to execute" },
      timeout: {
        type: "number",
        description: "Timeout in milliseconds (default: 120000, max: 600000)",
      },
    },
    required: ["command"],
  },
});
```

```js
registry.register("WebFetch", {
  description: `Fetches content from a URL, converts HTML to readable text, and processes it with a prompt.

Usage notes:
  - The URL must be a fully-formed valid URL
  - HTTP URLs will be automatically upgraded to HTTPS
  - The prompt should describe what information you want to extract from the page
  - Results may be summarized if the content is very large
  - Includes a self-cleaning 15-minute cache
  - For GitHub URLs, prefer using the gh CLI via Bash instead`,
  input_schema: {
    type: "object",
    properties: {
      url: { type: "string", description: "The URL to fetch content from" },
      prompt: {
        type: "string",
        description: "What information to extract from the page",
      },
    },
    required: ["url", "prompt"],
  },
});
```

```js
registry.register("Glob", {
  description:
    "Find files matching a glob pattern. Returns paths sorted by modification time.",
  input_schema: {
    type: "object",
    properties: {
      pattern: {
        type: "string",
        description: "Glob pattern (e.g. '**/*.js', 'src/**/*.ts')",
      },
      path: {
        type: "string",
        description: "Directory to search in (default: cwd)",
      },
    },
    required: ["pattern"],
  },
});
```

```js
registry.register("Grep", {
  description:
    "Search file contents using regex. Uses ripgrep (rg) if available, falls back to grep.",
  input_schema: {
    type: "object",
    properties: {
      pattern: { type: "string", description: "Regex pattern to search for" },
      path: {
        type: "string",
        description: "File or directory to search (default: cwd)",
      },
      glob: { type: "string", description: "File glob filter (e.g. '*.js')" },
      output_mode: {
        type: "string",
        enum: ["content", "files_with_matches", "count"],
        description: "Output mode (default: files_with_matches)",
      },
      "-i": { type: "boolean", description: "Case insensitive search" },
      "-n": { type: "boolean", description: "Show line numbers" },
      "-C": { type: "number", description: "Context lines around each match" },
      "-A": { type: "number", description: "Lines after each match" },
      "-B": { type: "number", description: "Lines before each match" },
      head_limit: {
        type: "number",
        description: "Limit output to first N results",
      },
    },
    required: ["pattern"],
  },
});
```

### Registering MCP Tools

Prefix the name with MCP and the name of the server!

```js
const toolName = `mcp__${name}__${tool.name}`;
registry.register(toolName, {
  description: tool.description || `MCP tool ${tool.name} from ${name}`,
  input_schema: tool.inputSchema || { type: "object", properties: {} },
});
```

### Register SubAgent Tool

```js
registry.register("Agent", {
  description:
    "Launch a sub-agent to handle a task. Available types: general-purpose, Explore, Plan.",
  input_schema: {
    type: "object",
    properties: {
      description: {
        type: "string",
        description: "A short (3-5 word) description of the task",
      },
      prompt: {
        type: "string",
        description: "The task for the agent to perform",
      },
      subagent_type: {
        type: "string",
        enum: ["general-purpose", "Explore", "Plan"],
        description: "Agent type",
      },
      model: {
        type: "string",
        enum: ["sonnet", "opus", "haiku"],
        description: "Optional model override",
      },
    },
    required: ["description", "prompt"],
  },
});
```

```js
registry.register("Agent", {
  description: `Launch a new agent to handle complex, multi-step tasks autonomously.
    Available agent types:
    - general-purpose: For complex tasks requiring multiple tools. Has access to all tools.
    - Explore: Fast, read-only agent for searching codebases. Uses haiku model.
    - Plan: Software architect for designing implementation plans. Read-only.
    - claude-code-guide: Documentation expert for Claude Code/API. Read-only, uses haiku.
    - verification: Adversarial agent that tries to break implementations. Cannot modify project files.

    Guidelines:
    - Use Explore for quick searches and codebase navigation
    - Use Plan for designing implementation strategies
    - Use general-purpose for tasks that require writing code or running commands
    - Use verification after implementing features to validate they work
    - Use run_in_background for tasks that don't need immediate results
    - Use isolation: "worktree" for tasks that modify code (prevents messing up main repo)`,
  input_schema: {
    type: "object",
    properties: {
      description: {
        type: "string",
        description: "A short (3-5 word) description of the task",
      },
      prompt: {
        type: "string",
        description: "The task for the agent to perform",
      },
      subagent_type: {
        type: "string",
        enum: [
          "general-purpose",
          "Explore",
          "Plan",
          "claude-code-guide",
          "verification",
        ],
        description: "The type of agent to use",
      },
      model: {
        type: "string",
        enum: ["sonnet", "opus", "haiku"],
        description: "Optional model override",
      },
      run_in_background: {
        type: "boolean",
        description:
          "Run agent in background. Returns immediately with agent ID.",
      },
      isolation: {
        type: "string",
        enum: ["worktree"],
        description: "Isolation mode. 'worktree' creates a git worktree.",
      },
    },
    required: ["description", "prompt"],
  },
});
```
