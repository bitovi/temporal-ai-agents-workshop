# Exercise 8 - Multi-Agent Orchestration

## Goals

Understand how to configure multiple LLM-based agents to collaborate on complex tasks that require diverse expertise. Multi-agent systems leverage the strengths of different models or specialized agents to achieve better outcomes than any single generalist agent.

## What you need to know

A single agent trying to handle research, reasoning, code generation, customer support, billing, and validation simultaneously tends to be mediocre at all of them — quality often degrades as context grows. Multi-agent orchestration mitigates this by breaking complex tasks across specialized agents, each operating within its own focused context window.

### Specialization Over Generalization

Specialized agents do their tasks more predictably, more efficiently, and with less context overhead than a single generalist. Each agent can be tuned via system prompt, model selection, and tool definitions for its domain — a quick lookup agent can run on a small fast model with a tight prompt, while a deep reasoning agent uses a frontier model with a richer prompt. Narrower instructions and tools also produce more consistent behavior since there are fewer ways to go off-script.

A billing agent doesn't need account verification logic or game-engine code, so we don't pay the context cost. This mirrors how human teams organize around expertise, and it extends our Plan & Execute pattern: when each sub-agent has a well-defined set of abilities, the top-level orchestrator can more easily decide where to delegate.

Tighter scope per agent also makes the overall system easier to control, evaluate, and improve over time.

### Parallelization

When multiple agents work on different parts of a task, we can parallelize to speed up the overall process — the same idea as the Plan & Execute Architecture, except each parallel "task" is itself a sub-agent doing work rather than a single tool call.

As long as subtasks don't share dependencies, they can execute concurrently. Parallel retrieval, analysis, or validation dramatically reduces wall-clock time, and a coordinator agent can merge or rank the outputs into a final result. This doesn't reduce total compute, but it lets us complete the task faster.

### Clear Boundaries

Agents work better with clear responsibilities and explicit instructions. Tight bounds on what an agent can do and is responsible for make that agent more effective and make it easier for other agents (and humans) to work with it.

- Clear ownership reduces overlap and confusion.
- Well-defined inputs and outputs simplify coordination.
- Boundaries make the system easier to test, replace, and scale.

Ultimately this all serves the same goal: keeping each agent's context window organized and well-defined.

### Context Window Management

Splitting tasks across multiple agents lets each agent operate within its own context window, preventing the orchestrator's context from being overloaded. Each sub-agent processes information independently and only a final distilled summary flows back up.

> Note: GitHub Copilot and Claude Code use this technique heavily — the main agent stays focused on the high-level plan while exploration, research, and other detailed tasks are delegated to sub-agents. You can often see this in the "thinking" steps where they announce a plan to spawn several sub-agents in parallel and then merge findings back into the main conversation.

For a concrete example, Claude Code ships with an `Explore` sub-agent: a fast, read-only agent specialized for searching codebases. It is locked down to glob/grep/read tools (no `Write`, `Edit`, `Bash`, or further `Agent` spawning), with a system prompt instructing parallel tool calls and efficient findings. The root Claude Code agent can spawn many `Explore` instances in parallel to divide-and-conquer searches across a large codebase.

```js
Explore: {
  description: "Fast read-only agent for searching and exploring codebases",
  readOnly: true,
  disallowedTools: ["Agent", "Write", "Edit", "Bash"],
  getSystemPrompt:
    () => `You are a file search specialist. You excel at rapidly navigating and exploring codebases.
          === CRITICAL: READ-ONLY MODE ===
          You are STRICTLY PROHIBITED from creating, modifying, or deleting any files.
          Your role is EXCLUSIVELY to search and analyze existing code.

          Your strengths:
          - Rapidly finding files using glob patterns
          - Searching code with powerful regex patterns
          - Reading and analyzing file contents

          Guidelines:
          - Use Glob for broad file pattern matching
          - Use Grep for searching file contents with regex
          - Use Read when you know the specific file path
          - Return file paths as absolute paths
          - Be fast and efficient — make parallel tool calls where possible
          - Avoid using emojis`,
}
```

Claude Code also ships sub-agents with much more narrow specializations. The `claude-code-guide` agent, for example, is a documentation expert dedicated to Claude Code, the Claude Agent SDK, and the Claude API. It's also read-only, with a prompt that constrains it to those three domains and prescribes a clear approach (identify the domain, fetch official docs, provide actionable guidance). A great illustration of how tightly scoped a specialized sub-agent can be.

```js
"claude-code-guide": {
  agentType: "claude-code-guide",
  description: "Documentation expert for Claude Code, Agent SDK, and Claude API",
  readOnly: true,
  disallowedTools: ["Agent", "Write", "Edit", "Bash"],
  getSystemPrompt:
    () => `You are the Claude guide agent. Your primary responsibility is helping users understand and use Claude Code, the Claude Agent SDK, and the Claude API effectively.
          Three domains of expertise:
          1. Claude Code (the CLI tool)
          2. Claude Agent SDK (Node.js/TypeScript and Python)
          3. Claude API (formerly Anthropic API)

          Approach:
          1. Determine which domain the question falls into
          2. Use WebFetch to fetch relevant documentation
          3. Provide clear, actionable guidance with examples
          4. Use WebSearch if docs don't cover the topic
          5. Reference local project files when relevant

          Guidelines:
          - Prioritize official documentation
          - Keep responses concise and actionable
          - Include code examples when helpful
          - Avoid using emojis`,
}
```

If a sub-agent fails, the orchestrator can handle it gracefully without polluting the main context with error details.

## Local Agents vs Remote Agents

There are two main ways to build multi-agent systems, and they map onto two layers of tooling: frameworks and protocols.

### Local framework-level orchestration

This is the model used by Claude Code, GitHub Copilot, and most multi-agent frameworks today.

- Agents live in the same application, often the same process, under one top-level orchestrator.
- Sub-agents are typically invoked like tools — the root agent delegates a task (sometimes to many parallel sub-agents) and waits for the result.
- Communication is usually synchronous and centrally controlled. The orchestrator handles delegation, context, and errors; sub-agents share resources directly.
- Sub-agents are tightly coupled to the orchestrator and not designed to operate independently.

### Remote protocol-level collaboration

The other approach is protocol-based, where our agent talks to external agents across a network boundary.

- The remote agent may be a separate system — different team or company, different environment, different language, different models or tools.
- External agents are peers or collaborators rather than internal helpers.
- Interaction is often asynchronous, stateful, and loosely coupled. We may need to negotiate, exchange messages over time, and handle latency and failure differently than a local tool call.
- It looks less like an internal function call and more like working with another user or service.

This is exactly what the Agent2Agent (A2A) Protocol is designed to address. Before we dig into A2A, it's worth looking at the Agent Development Kit (ADK), since it shows up in both the local and remote pictures.

## Frameworks & SDKs

A quick survey of the major multi-agent frameworks, focused on architectural differences rather than API tutorials.

### LangGraph (LangChain)

- Graph-based orchestration: agents as nodes, edges define control flow
- Typed state channels — passes only state deltas, not full history (most token-efficient in benchmarks)
- Pre-built packages: Supervisor, Swarm, Computer Use Agent
- Supports hierarchical multi-level supervisors
- Checkpointed state for long-running workflows and human-in-the-loop
- MIT licensed, Python and JS

### OpenAI Agents SDK (successor to Swarm)

- Minimalist: four primitives — Agents, Handoffs, Guardrails, Tracing
- Production-ready evolution of the experimental Swarm framework (March 2025)
- Handoff pattern: agents declare handoff targets, framework enforces valid paths
- Provider-agnostic (works with 100+ LLMs via Chat Completions API)
- Python and TypeScript support
- Sessions for persistent working context, MCP server tool integration

### Google Agent Development Kit (ADK)

Google's Agent Development Kit was originally developed as a Python framework for building AI agents, and has since expanded to also support Go, TypeScript, and — as of this week — Java, with the **ADK for Java 1.0.0** release. ADK is particularly interesting for this exercise because it ships with concrete multi-agent implementation examples we can learn from, and its agents have native support for the A2A Protocol out of the box.

- Launched at Google Cloud NEXT 2025, open-sourced
- Workflow agents: `SequentialAgent`, `ParallelAgent`, `LoopAgent` — deterministic, no LLM needed for orchestration
- `LlmAgent` transfer for dynamic routing
- Agent hierarchy with parent/sub-agent tree structure (single parent rule)
- `CustomAgent` via `BaseAgent` extension for arbitrary orchestration logic
- Python, TypeScript, Go, and Java SDKs (Java 1.0.0 released in March 2026)
- Native A2A Protocol support
- Same framework powering Google's Agentspace and Customer Engagement Suite
- ADK 2.0 Alpha: graph-based workflow support

### Microsoft Agent Framework (AutoGen + Semantic Kernel)

- Direct successor combining AutoGen's multi-agent abstractions with Semantic Kernel's enterprise features
- Graph-based workflows for explicit multi-agent orchestration
- Session-based state management, type safety, middleware, telemetry
- Conversation-loop pattern (AssistantAgent ↔ UserProxyAgent)
- Strong enterprise integration (Azure ecosystem)
- Python, C#, and Java

### CrewAI

- Role-based agent teams with built-in delegation and memory
- Higher-level abstraction than LangGraph — define agent roles, goals, and backstories
- Good for hierarchical team structures
- Trade-off: higher token overhead due to agent-to-tool gap and memory management
- Python

## Multi-Agent System Patterns

We can take a lot of inspiration from distributed systems when designing multi-agent systems. The same trade-offs around coordination cost, fault isolation, throughput, and observability apply.

### Orchestrator-Worker (Hub and Spoke)

The most widely deployed pattern in production. A central orchestrator receives tasks, decomposes them, routes subtasks to specialized workers, and aggregates results. Workers don't communicate directly — all coordination flows through the orchestrator.

- Orchestrator maintains global state and handles error recovery
- Workers are stateless and focused on a single capability
- Trade-off: orchestrator is a single point of failure and potential bottleneck
- Examples: LangGraph Supervisor, AutoGen group chat with selector agent

#### Example: Orchestrator-Worker in ADK

ADK provides simple abstractions for this pattern: define agents, then promote one to root by giving it a list of sub-agents to manage. Below we define a **Billing** agent, a **Support** agent, and a **Help Desk Coordinator** that routes requests to whichever sub-agent fits best:

```js
LlmAgent billingAgent = LlmAgent.builder()
    .name("Billing")
    .description("Handles billing inquiries and payment issues.")
    .build();

LlmAgent supportAgent = LlmAgent.builder()
    .name("Support")
    .description("Handles technical support requests and login problems.")
    .build();

LlmAgent coordinator = LlmAgent.builder()
    .name("HelpDeskCoordinator")
    .model("gemini-2.0-flash")
    .instruction("Route user requests: Use Billing agent for payment issues, Support agent for technical problems")
```

### Handoff / Swarm (Decentralized)

Agents transfer control to each other explicitly via "handoff" functions. No central supervisor — each agent decides locally whether to handle a task or pass it to a peer. Originated from OpenAI's experimental Swarm framework, now production-grade in the OpenAI Agents SDK.

- Lightweight, stateless between calls
- Agents declare handoff targets; framework enforces valid paths
- Risk: handoff loops (Agent A → Agent B → Agent A) without guard conditions
- Best for: high-volume, well-defined routing (customer support triage, onboarding flows)

### Hierarchical (Tree-Structured Delegation)

Multi-level delegation: a top-level manager delegates to mid-level supervisors, who delegate to leaf-level workers. Each level adds abstraction — strategy at top, tactics in middle, execution at leaves.

- Enables 50+ agent deployments across business domains
- Each supervisor manages a "team" of agents
- LangGraph supports this natively: supervisors that manage other supervisors
- Google ADK models this with agent hierarchy trees (parent/sub-agent relationships)

### Pipeline (Sequential Stages)

Linear assembly line — Agent A completes, passes output to Agent B, then Agent C. Deterministic, easy to debug, great for data processing workflows.

- Google ADK's `SequentialAgent` primitive
- Common in: ETL pipelines, document processing (parse → extract → summarize), content generation with review

#### Example: Sequential code-writing pipeline

A common use case for a sequential pipeline is writing code through specialized passes:

- **Code Writer** — generates the initial implementation from a specification.
- **Code Reviewer** — adversarially reviews the code for errors, style, and best practices.
- **Code Refactorer** — refactors based on the reviewer's findings.

A `SequentialAgent` is a perfect fit, ensuring code is written, reviewed, and refactored in a strict order:

```java
LlmAgent writer = LlmAgent.builder()
    .name("CodeWriter")
    .instruction("Write Java code to fulfill the given requirements")
    .outputKey("java_code")
    .build();

LlmAgent reviewer = LlmAgent.builder()
    .name("CodeReviewer")
    .instruction("Review the Java code in {java_code} and ensure it meets the requirements")
    .outputKey("review")
    .build();

LlmAgent refactor = LlmAgent.builder()
    .name("RefactorWriter")
    .instruction("Report the result from {review} and if there are issues, refactor the code in {java_code} to address them")
    .build();

SequentialAgent javaPipeline = SequentialAgent.builder()
    .name("JavaPipeline")
    .subAgents(writer, reviewer, refactor)
    .build();
```

### Parallel Fan-Out / Fan-In

Spawn multiple agents concurrently on independent subtasks, then synthesize results. Can be combined with pipeline stages.

- Google ADK's `ParallelAgent` primitive
- Example: code review where security auditor, style enforcer, and performance analyst all review a PR simultaneously, then a synthesizer combines feedback
- Race condition awareness: parallel agents sharing session state need unique write keys

### Loop / Iterative Refinement

Generator-Critic pattern: one agent produces output, another reviews it against criteria, loop until quality gate passes.

- Google ADK's `LoopAgent` with exit conditions
- Common in: code generation + validation, content creation + compliance review, self-improving agents

## Communication & State Management Between Agents

How agents actually share information — this is where the distributed systems parallels get concrete.

### Shared State / Scratchpad

All agents read/write to a common state object. Simple, but risks context bloat and race conditions.

- LangGraph's typed state channels (pass only necessary state deltas, not full history)
- Google ADK's `session.state` with key templating (`{my_key}` in instructions)
- CrewAI's shared memory objects

### Message Passing

Agents communicate via structured messages. More explicit than shared state but requires defining message schemas.

- AutoGen's conversation-loop pattern (AssistantAgent ↔ UserProxyAgent message passing)
- A2A Protocol's Message/Part model (TextPart, FilePart, DataPart)

### Context Isolation as a Feature

Sub-agents getting fresh context windows isn't a limitation — it's the point. A research sub-agent can chew through hundreds of documents without polluting the orchestrator's context. Only the distilled summary flows back up. This is the key architectural insight behind Claude Code's sub-agent model.

### When NOT to Multi-Agent

Like any distributed system, multi-agent architectures introduce complexity that can outweigh their benefits. Always start with a single agent; only move to multi-agent when there are clear, unavoidable limitations.

Failure modes to be aware of:

- **Cascading failures.** One agent's bad output becomes another's bad input. Each handoff is an error amplification point.
- **Coordination overhead.** Every additional agent adds latency from routing decisions and context management.
- **Observability challenges.** Debugging "why did the user end up at Agent F instead of Agent D?" requires production-grade distributed tracing.
- **Single-agent ceiling test.** If your agent works well with fewer tools and a focused prompt, you don't need multi-agent. Refactor the prompt before reaching for orchestration.

## Practical Examples

Claude Code and GitHub Copilot both use multi-agent architectures. Each spawns sub-agents with their own context windows, system prompts, and tool definitions; the main agent acts as orchestrator, delegating focused subtasks and receiving back concise results.

Claude Code's built-in sub-agents:

- **General Purpose** — general work
- **Explore** — codebase navigation
- **Plan** — software architect that builds implementation plans
- **claude-code-guide** — documentation expert for Claude Code itself
- **Verification** — adversarial agent that tries to break implementations and find edge cases

Simple sub-agents are spawned via this tool call:

```js
{
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
}
```

More complex multi-step agents add background execution, isolation modes, and additional sub-agent types:

```js
{
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
}
```

## Take Advantage of Temporal

How do these multi-agent patterns map to Temporal's primitives? Temporal already solves a lot of distributed-systems concerns — state management, fault tolerance, orchestration — so we can lean on it rather than reinventing the wheel.

- **Orchestrator-Worker** — Temporal Workflow as orchestrator; Activities or Child Workflows as workers.
- **Parallel Fan-Out** — `Promise.all()` on multiple Activity invocations.
- **Pipeline** — Sequential Activity execution within a Workflow.
- **Hierarchical** — Parent Workflows delegating to Child Workflows.
- **Handoffs** — Signal-based communication between Workflows.
- **State** — Temporal's event-sourced Workflow state replaces framework-specific state management.
- **Fault tolerance** — Built-in retry policies, timeouts, and saga patterns.

## ADK Remote Agents

ADK doesn't only handle local sub-agents — it also makes plugging in remote agents easy, as long as they speak the A2A Protocol. ADK provides a `RemoteA2AAgent` abstraction that wraps an A2A client and exposes it as just another `BaseAgent`. From the orchestrator's point of view, a remote agent looks identical to a local one — ADK handles the network translation.

```java
// 1. Resolve the public AgentCard from the remote agents endpoint
AgentCard publicAgentCard = new A2ACardResolver(
    "http://localhost:9090", "http://localhost:9090/.well-known/agent-card.json"
).getAgentCard();

// 2. Build the official A2A SDK Client using the resolved card and transport
Client a2aClient = Client.builder(publicAgentCard)
    .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
    .clientConfig(
        new ClientConfig.Builder().setStreaming(publicAgentCard.capabilities().streaming()).build()
    ).build();

// 3. Wrap it in the ADK's RemoteA2AAgent natively
BaseAgent remotePrimeAgent = RemoteA2AAgent.builder()
    .name(publicAgentCard.name())
    .a2aClient(a2aClient)
    .agentCard(publicAgentCard)
    .build();
```

> Note: Because ADK for Java 1.0.0 was just released, this exercise doesn't yet include a full Java + remote-agent example. We'll see the equivalent integration in TypeScript later. For now, let's look at how A2A itself works under the hood.

## The Agent2Agent Protocol

The Agent-to-Agent Protocol is an open standard from Google (now managed by the Linux Foundation) that lets AI agents communicate and collaborate in a structured way regardless of framework, vendor, or underlying tech. It addresses many of the challenges Google encountered while deploying large-scale multi-agent systems internally and for customers, and gives agents from different teams or companies a common language to collaborate as peers.

A2A defines standard agent cards, authentication and authorization mechanisms for controlling access, and supports long-running tasks without exposing internal state between agents.

In this exercise, our personal assistant agent (Java/Temporal) uses the [A2A Java SDK](https://github.com/a2aproject/a2a-java-sdk) as a client to communicate with a remote Riot Games support agent (TypeScript) built with the [A2A JS SDK](https://github.com/a2aproject/a2a-js).

### Design Principles

- **Embrace agentic capabilities** — Agents collaborate naturally without sharing memory, tools, or context, enabling true multi-agent scenarios across organizational boundaries.
- **Build on existing standards** — HTTP, Server-Sent Events, JSON-RPC; integrates easily with existing enterprise stacks.
- **Support long-running tasks** — Quick request/response through to multi-day work (potentially with human intervention), with real-time feedback and status updates.
- **Secure by default** — Designed for enterprise auth/authz so only authorized callers can access an agent.
- **Modality agnostic** — Text, audio, video, forms, iframes, etc.

### How it works

The A2A protocol follows a client-server model. Our personal assistant is the A2A **client** and the support agent is the A2A **server**.

#### Participants

There are three participants in any A2A interaction:

- **User** — the human using the agent system to accomplish a task. The user talks to the client, not directly to the server.
- **Client** — the entity, usually an agent, that represents the user. It requests actions from the remote agent on the user's behalf. In our example, this is the personal assistant.
- **Server** — the remote agent providing some service. From the user's and client's perspective the server is a black box: neither party knows anything about its internal implementation, models, or tools.

So how does the client discover that the remote agent exists, learn what it can do, and figure out how to talk to it? That's where A2A's core concepts come in.

#### Core concepts

A2A defines a small set of core concepts that the client and server use to exchange work:

- **Agent Card** — a JSON file describing an agent's capabilities. The agent's creator publishes it at a well-known URL on the web so that other agents can discover and understand what the agent does. Once a client has ingested an Agent Card, it knows the remote agent's name, description, version, service endpoint URL, supported modalities, authentication requirements, and skills. Agent Cards are conceptually similar to model cards for LLMs — a business card or résumé that lets agents discover each other.
- **Task** — a unit of work needed to accomplish a request. Each task has a unique ID and progresses through a defined lifecycle (`submitted`, `working`, `input-required`, `completed`, `failed`). Tasks carry their status, history, and any artifacts produced so far, which makes them well suited to multi-turn processing or long-running agent-to-agent collaboration.
- **Artifact** — the tangible product the server produces as the result of a task — a document, image, spreadsheet, or other deliverable. Artifacts consist of one or more parts and can be incrementally streamed.
- **Message** — a single exchange or turn in the conversation between client and server. Messages relay instructions, context, thoughts, questions, replies, and status updates. Each message has a role (`agent` for server-sent messages, `user` for client-sent messages) and contains one or more parts.
- **Part** — a piece of content inside a message or artifact. Parts come in several types depending on the data they carry: a `TextPart` for text, a `FilePart` for files, and a `DataPart` for structured JSON data.

Let's go a bit deeper into each of these.

- Agent card
  - This JSON file outlines agentic AI metadata and can be accessed using a URL. It contains basic information about an agent, including its name, description, version, service endpoint URL, supported modalities or data types and authentication requirements.
  - Agent cards are similar to model cards for large language models (LLMs). They also advertise an agent’s capabilities and skills, serving as a business card, résumé or LinkedIn profile that allows agents to discover each other.
  - The official recommendation is to host the Agent Card at a well-known path under the agent's service URL — typically `/.well-known/agent-card.json`. For example, if Riot Games published a customer support agent, you might expect to find its card at <https://support.riotgames.com/.well-known/agent-card.json>. Clients can then retrieve the card via a simple HTTP `GET` request and learn everything they need to interact with the agent.
  - There are several ways a client agent might actually find these URLs:
    - **Explicit configuration** — we hand the client a list of cards directly so it knows which remote agents are available.
    - **Public registries** — community or vendor-hosted catalogs of A2A-capable agents the client can query, often exposed as an MCP tool.
    - **Private registries** — internal company-hosted catalogs of A2A-capable agents, also typically exposed as an MCP tool.
    - **Decentralized discovery** — because discovery is just "fetch some URL," nothing stops more peer-to-peer or blockchain-style registries from emerging, allowing agents to form self-organized networks (with significant security implications, which we'll come back to).

- Task
  - A task represents a stateful unit of work — a single project or objective — between the client and the remote agent. It allows them to collaborate towards a specific outcome and generate corresponding output artifacts.
  - Each task has a unique ID and progresses through a clearly defined lifecycle that tracks its progress towards the goal:
    - **submitted** — the client has created the task.
    - **working** — the remote agent is actively processing the request.
    - **input-required** — the remote agent needs additional information from the client (clarification of instructions, the user's email or account ID, a confirmation, etc.) before it can continue. This is what makes A2A genuinely collaborative rather than fire-and-forget.
    - **completed** — the task finished successfully and any artifacts have been delivered.
    - **canceled** — the client or server stopped the task because the output is no longer needed.
    - **failed** — something went wrong and the remote agent was unable to complete the request.
  - Tasks are the right unit of work for multi-turn processing or long-running agent-to-agent collaboration.
  - A few rules govern how tasks are owned and evolve:
    - **Task creation** — tasks are always created by the client (the local agent) as a request to a remote agent.
    - **State management** — only the server (the remote agent) sets the task's state. The client observes status changes but doesn't drive them.
    - **Continuous interaction** — the server can transition the task back to `input-required` at any point to ask for more information, making the interaction a back-and-forth rather than fire-and-forget.
    - **Information transfer** — like the user/agent message history we tracked in our Temporal workflows, each task carries a stateful, multi-turn history of the conversation between client and server.
    - **Session association (optional)** — multiple related tasks can be grouped under a shared `sessionId` so agents can carry additional context across tasks that belong together.
  - As a data structure, a task is roughly what you'd expect: a task ID, the full message history, the current status, any artifacts produced so far, and arbitrary metadata.

```js
interface Task {
  id: string; // Unique identifier
  sessionId: string // Client-generated session id
  status: TaskStatus; // Current status of the task
  history?: Message[]; // Message log history
  artifacts: Artifact[]; // Collection of artifacts created by the agent
  metadata?: Record<string, any>; // Extended metadata
}

interface TaskStatus {
  state: TaskState;
  message?: Message; // Additional status update provided to the client
  timestamp?: string; // ISO datetime value
}

type TaskState = "submitted" | "working" | "input-required" | "completed" | "canceled" | "failed" | "unknown";
```

#### How a remote agent can respond to a task

When the server receives a task, it isn't limited to a simple "do the work and return a result" flow. The remote agent can choose to:

- Satisfy the request immediately
- Schedule the work to be performed later
- Reject the request
- Negotiate a different execution method
- Request more information from the client (by transitioning the task to `input-required`)
- Delegate the work to other agents or downstream systems

- Message
  - Messages are how the client and server actually move information back and forth during a task. While artifacts are the **final output** of a task, messages carry everything else that happens along the way.
  - Each message represents a single exchange or turn in the conversation and contains one or more parts holding the actual content. Each message has a role — `agent` for server-sent messages, `user` for client-sent messages.
  - Messages are used to transmit:
    - **User input** — the initial request, follow-up questions, instructions, and additional context.
    - **Control flow** — clarifications, confirmations, and other intermediate back-and-forth needed to make progress.
    - **Status updates** — progress reports while the server is working on a task.
    - **Agent thinking and reasoning** — intermediate steps the server wants to share with the client.
    - **Error information** — failure details, validation errors, or any other metadata related to the task.
  - A message is made up of three sections:
    - **Role** — the sender of the message, usually either `user` (from the client) or `agent` (from the server).
    - **Parts** — the actual content of the message, with a type (text, file, data) and the corresponding data.
    - **Metadata** — an optional field for carrying additional information alongside the message.

```js
interface Message {
  role: "user" | "agent"; // Sender's role
  parts: Part[]; // Message content
  metadata?: Record<string, any>; // Extended metadata
}
```

- Artifact
  - An artifact represents the output generated by the remote agent as the final result of a task — the deliverable that the client and server were collaborating to produce.
  - It acts as a wrapper around some content: text, code, images, documents, spreadsheets, or any other deliverable.
  - A single task can produce multiple artifacts. Asking the agent to "say hello to a user named X" might return a single artifact with a single text part. Asking it to "build me a landing page" might return many — an HTML file, a CSS file, a JavaScript file, and several image artifacts — all from one task.
  - Key features:
    - **Immutability** — once generated, the content of an artifact is immutable.
    - **Named** — artifacts have a `name` for easy identification and reference.
    - **Multi-part** — an artifact can contain multiple parts, each with its own content and type.
    - **Streaming support** — using streaming responses (`append: true`), new parts can be appended to an existing artifact, which is useful for gradual content generation or large data transfer.

```js
interface Artifact {
  name?: string; // Optional name for the artifact
  description?: string; // Optional description for the artifact
  parts: Part[]; // Array of parts contained within the artifact
  metadata?: Record<string, any>; // Extended metadata
  index: number; // Index of the artifact within its owning task
  append?: boolean; // Whether appending Parts is allowed (for streaming)
  lastChunk?: boolean; // Whether this is the last chunk in a stream
}
```

- Part
  - A part is the single unit of content that makes up a message or an artifact. The message or artifact is essentially a wrapper — the part is the thing that actually carries the data.
  - Parts let different types of data be combined and exchanged within a single message or artifact.
  - Parts have various types based on the data they carry: a `TextPart` for text, a `FilePart` for files, and a `DataPart` for structured JSON data.
  - Each part is made up of three things:
    - **Content** — the specific piece of data the part carries.
    - **Content type** — an identifier for the type of data, typically a MIME type (`text/plain`, `image/png`, `application/json`, etc.).
    - **Metadata** — any additional information you want to attach to this part.
  - Parts are intentionally generic. By combining many different parts together, messages and artifacts can fully represent complex information — text instructions alongside files, JSON data, images, or audio. A2A deliberately doesn't limit what you can build with this primitive.

#### Text Part structure

```java
record TextPart(String text) implements Part<String> {
    public static final String TEXT = "text";

    public TextPart (String text) {
        Assert.checkNotNullParam("text", text);
        this.text = text;
    }
}
```

#### Data Part structure

```java
record DataPart(Object data) implements Part<Object> {
    public static final String DATA = "data";

    public DataPart (Object data) {
        Assert.checkNotNullParam("data", data);
        this.data = data;
    }

    public static DataPart fromJson(String json) {
        Assert.checkNotNullParam("json", json);
        try {
            Object data = JSON_PARSER.fromJson(json, Object.class);
            return new DataPart(data);
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Invalid JSON: " + json, e);
        }
    }
}
```

#### File Part structure

```java
record FilePart(FileContent file) implements Part<FileContent> {
    public static final String FILE = "file";

    public FilePart (FileContent file) {
        Assert.checkNotNullParam("file", file);
        this.file = file;
    }
}

record FileContent(String mimeType, String name, ByteSource source) {
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB
    // In the A2A SDK there is a lot more checking here, conversion to base64, etc
}
```

The A2A protocol follows a client-server model setup with a three-step workflow:

- Discovery
- Authentication
- Communication

Communication starts with a client agent sending a task to the chosen remote agent. Agent-to-agent communication occurs over HTTPS for secure transport, with JSON-RPC (Remote Procedure Call) 2.0 as the format for data exchange.

The remote agent then processes the task. If it requires more information, it notifies the client agent asking for additional details. Once it completes the task, the remote agent sends a message to the client agent along with any generated artifacts.

A2A also provides task management features for more complex tasks that can’t be completed immediately, such as those needing human intervention or involving multiple steps. In the case of long-running tasks that take hours or days or if a client agent gets disconnected, the A2A protocol allows for asynchronous updates through push notifications sent to a secure client-supplied webhook. For large or long outputs or continuous status updates, the A2A protocol supports real-time streaming using server-sent events (SSE).

### MCP vs A2A

Previously introduced by Anthropic in 2024, the Model Context Protocol (MCP) serves as a standardization layer for AI applications to communicate effectively with external services, such as APIs (application programming interfaces), data sources, predefined functions and other tools. Meanwhile, the A2A protocol focuses on agent collaboration, facilitating communication between AI agents.

When deciding between the two:

- **Reach for A2A when** you need multiple agents to collaborate on complex tasks involving multi-turn negotiation, where the participating agents have autonomous decision-making and need to delegate work and aggregate results.
- **Reach for MCP when** an agent needs access to external tools or data — structured, predefined operations like calling APIs, querying databases, or integrating with traditional (non-agent) services.
- **Use both together** by treating them as complementary layers: A2A at the **coordination layer** between agents, and MCP at the **execution layer** so each agent can pull in the data and tools it needs to do its job.

Both protocols are meant to complement each other. For example, a retail store might have its own inventory agent that uses MCP to interact with databases storing information about products and stock levels. If the inventory agent detects products low in stock, it notifies an internal order agent, which then uses A2A to communicate with external supplier agents and place orders.

### Streaming vs Asynchronous Delivery

A2A is designed for tasks that may run for seconds, minutes, or longer, so the protocol gives you two primary delivery modes:

- **Continuous streaming over SSE** — while the task is in progress, the client keeps an open connection to the remote agent and receives updates and artifact chunks over Server-Sent Events. The reference implementation we'll look at shortly uses this mode.
- **Asynchronous notifications via webhook** — for long-running tasks (hours, days, or longer) where keeping a persistent connection isn't realistic, the server can push status updates and results to a client-supplied webhook. This is gated by the `capabilities.pushNotifications: true` capability on the agent card.

Many production systems combine both: stream first while the connection is healthy, then fall back to push notifications (and/or polling) if the connection drops or the task is extremely long-running.

#### A2A Push Notifications

When we create our A2A client we can optionally pass a push notification config that tells the remote agent which URL we expect to receive task updates on. The remote agent will then `POST` task status changes and results to that webhook instead of (or in addition to) sending them over a streaming connection.

Of course, our client agent also needs to actually expose that webhook endpoint, which usually means bringing in something like Spring Boot (or any HTTP server framework) so the agent can listen for incoming notifications.

```java
PushNotificationConfig pushConfig = new PushNotificationConfig.Builder()
      .url("https://my-agent.bitovi.com/a2a/webhook")
      .token("verify-token")
      .build();

ClientConfig clientConfig = new ClientConfig.Builder()
      .setStreaming(true) // primary: use streaming
      .setPushNotificationConfig(pushConfig) // backup: also push to webhook
      .setAcceptedOutputModes(List.of("text", "data"))
      .build()

Client client = Client.builder(card)
      .clientConfig(clientConfig)
      .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
      .build();
```

### Agent Discovery

A2A standardizes **how** an agent describes itself — via the Agent Card — while deliberately leaving room for **multiple discovery strategies** so the protocol can fit different deployment environments. That flexibility lets A2A work for very different audiences:

- **Decision makers** need governance, trust boundaries, and operational visibility — they care about which agents are allowed to discover and call which others.
- **Beginners** need a clear, low-friction way to find compatible agents to start experimenting with.
- **Developers** need reliable metadata for authentication, capabilities, and routing so they can wire agents together with confidence.
- **Advanced teams** need selective disclosure and policy-driven discovery — exposing different surface area to different callers, or routing based on organizational policy.

The discovery mechanisms covered earlier (well-known URLs, explicit configuration, public/private registries, and decentralized peer-to-peer discovery) all plug into this same Agent Card model — the card stays the standard contract while organizations choose the discovery strategy that matches their governance and trust requirements.

#### Discovery Strategies

A2A doesn't dictate **how** Agent Cards make their way into your system, only what they look like once they get there. The most common strategies are:

- **Well-known endpoint** — the agent hosts its card at a well-known URL (typically `/.well-known/agent-card.json`) and clients fetch it with a simple HTTP `GET`, optionally with authorization. Best for public or domain-controlled discovery. Pair with endpoint protection if the card content is sensitive.
- **Curated registry** — a central catalog (public or organization-internal) indexes Agent Cards from many agents. Useful for enterprise governance, policy filtering, and capability search, and a natural fit for exposure as an MCP tool.
- **Private configuration** — card payloads are loaded via internal configuration, secrets management, or internal-only APIs. A good fit when you only want A2A integration to operate inside the boundary of your own company or product.

On top of whichever strategy you pick, the normal API hardening best practices still apply: serve cards over TLS, authenticate callers (OAuth or otherwise), and make use of the `supportsAuthenticatedExtendedCard` mechanism to reveal additional capabilities only to authenticated users.

### Agent Card

The support agent in this exercise advertises itself via an agent card at `.well-known/agent-card.json`. You can see the card definition in `support-agent-server/server.ts`. Here's what it looks like:

#### Agent Card structure

A typical Agent Card breaks down into three groups of fields:

- **Basic information** — name, description, service URL, provider information, version, and (optionally) a link to documentation. This is what tells a client what the agent is and where to reach it.
- **Capabilities** — how the agent should be communicated with: does it support streaming, push notifications, state transition history, and which interaction modes (input/output MIME types like text, images, or PDFs) it accepts and produces.
- **Authentication** — which authentication schemes the agent supports (e.g. basic, bearer), whether credentials are required, and the `supportsAuthenticatedExtendedCard` flag.
  - `supportsAuthenticatedExtendedCard` is a useful security feature: the public, unauthenticated card can advertise only a baseline of skills and capabilities, while authenticated clients receive an **extended** Agent Card with private features. This lets you keep some skills hidden from anonymous discovery while still exposing them to trusted callers.

```ts
const agentCard: AgentCard = {
  name: "Riot Games Support Agent",
  description:
    "Handles billing inquiries, refunds, and account issues for Riot Games.",
  protocolVersion: "0.3.0",
  url: `http://${HOST}:${HTTP_PORT}/a2a/jsonrpc`,
  skills: [
    {
      id: "billing",
      name: "Billing Support",
      description: "Refunds, duplicate charges, payment issues",
    },
    {
      id: "account",
      name: "Account Support",
      description: "Account verification, password resets",
    },
  ],
  defaultInputModes: ["text"],
  defaultOutputModes: ["text", "data"],
  additionalInterfaces: [
    { url: `http://${HOST}:${HTTP_PORT}/a2a/jsonrpc`, transport: "JSONRPC" },
    { url: `http://${HOST}:${HTTP_PORT}/a2a/rest`, transport: "HTTP+JSON" },
    { url: `${HOST}:${GRPC_PORT}`, transport: "GRPC" },
  ],
};
```

### A2A Discovery in this exercise

Our personal assistant in this repository takes the **curated registry / private configuration** approach to discovery. Agents aren't tools — but the only way an agent can interact with the outside world is through tool calls — so we expose a small set of tools that let the agent look up other A2A agents and then talk to them:

- `search_agent_registry` — searches our internal registry of known A2A agents and returns matching Agent Cards.
- `a2a_send_message` — kicks off a new task with one of those agents, or sends a follow-up message to an existing task.

```java
public class ToolRegistry {
    private static final Map<String, ToolFunction<String, ToolInput>, String>> toolExecutors = new HashMap<>();

    static {
        // A2A agent discovery and communication tools
        toolExecutors.put("search_agent_registry", AgentRegistryTool::execute);
        toolExecutors.put("a2a_send_message", A2ATool::execute);
    }

    public static List<Tool> getAllBedrockTools() {
        List<Tool> tools = new ArrayList<>();
        tools.add(AgentRegistryTool.getBedrockTool());
        tools.add(A2ATool.getBedrockTool());
        return tools;
    }
}
```

#### A2A Registry Tool

In this exercise, the registry behind `search_agent_registry` is just a hardcoded list of agents. When the tool is called, the local agent can optionally pass in a search query, and the `matches` function inspects each agent's name, description, and tags to see if it's a good fit for the request. Matching agents are returned to the caller.

In a real implementation this would typically go out to a central registry service, or return a curated set of pre-configured agents for an internal solution. It could also reach out to the internet — looking up companies and providers relevant to the query and dynamically checking whether they expose an Agent Card at `/.well-known/agent-card.json`.

```java
public class AgentRegistryTool {
    public record AgentEntry(String name, String url, String description,List<String> tags) {}

    private static final List<AgentEntry> REGISTRY = List.of(
            new AgentEntry("Riot Games Support Agent", "http://localhost:4000",
                "Handles billing inquiries, refunds, and account issues for Riot Games.",
                List.of("support", "billing", "refunds", "account", "riot games", "gaming")));

    public static String execute(String toolName, Map<String, Object> toolUseInput) {
        ToolInput input = validateToolInput(toolUseInput);
        List<Map<String, Object>> results = new ArrayList<>();
        for (AgentEntry agent : REGISTRY) {
            if (matches(agent, input)) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("name", agent.name());
                entry.put("url", agent.url());
                entry.put("description", agent.description());
                entry.put("tags", agent.tags());
                results.add(entry);
            }
        }

        System.out.println("[AgentRegistryTool] Found " + results.size() + " agent(s)");
        return gson.toJson(Map.of("agents", results));
    }
}
```

#### A2A Tasks & Messages

The other half of the discovery + communication pair is the `A2ATool`, which handles all of the actual messaging between our local agent and the remote agent.

`validateToolParams` enforces the shape of the input — we always need an `agentUrl` and a `message`, and for follow-up turns on an existing task we also expect a `taskId` and `contextId`.

Building the outgoing `Message` is straightforward in either case:

- **First message in a new task** — we send a user text message with no task or context IDs, and the A2A SDK takes care of creating the new task (and assigning a `taskId`) on the remote side.
- **Follow-up message on an existing task** — we attach the existing `contextId` and `taskId` so the remote agent knows which ongoing task this message belongs to.

Once the message is ready, we need a connection to the remote agent. Connections are cached per `agentUrl`, so if we've spoken with this remote agent before we just reuse the existing `AgentConnection`. If not, we resolve the remote agent's Agent Card via `A2ACardResolver`, build an `A2AClient` from that card, wrap it in a new `AgentConnection`, and store it for next time.

Finally, the message and the (cached or fresh) connection are handed off to `A2AHandler.sendAndCollect`, which actually drives the request/response with the remote agent.

```java
public class A2ATool {
    public static String execute(String toolName, Map<String, Object> toolUseInput) {
        A2ARequestInput params = A2AHelpers.validateToolParams(toolUseInput);
        Message message = params.existingTask()
            ? A2A.createUserTextMessage(params.message(), params.contextId(),params.taskId())
            : A2A.createUserTextMessage(params.message(), null, null);
        AgentConnection conn = getOrCreateConnection(params.agentUrl());
        return A2AHandler.sendAndCollect(conn, message);
    }

    public static AgentConnection getOrCreateConnection(String agentUrl) throws Exception {
        AgentConnection existing = connections.get(agentUrl);
        if (existing != null) return existing;

        AgentCard card = new A2ACardResolver(agentUrl).getAgentCard();

        A2AClient client = A2AClient.builder(card).build();
        AgentConnection conn = new AgentConnection(client, card);
        connections.put(agentUrl, conn);
        return conn;
    }
}
```

#### A2A Event Processing

The `A2AHandler.process` method does the actual work of sending the message and reacting to whatever the remote agent sends back. It's a fair amount of code, so we'll walk through it in smaller chunks — but at a high level it's just setting up a list of event consumers and then calling `sendMessage`.

There are two main event types we care about:

- **`TaskUpdateEvent`** — used for the streaming case, where the remote agent emits multiple updates over the life of the task (status transitions and artifact chunks).
- **`TaskEvent`** — used for the non-streaming case, where the remote agent returns a single final `Task` object that we inspect for its status, history, and artifacts.

At the top of the method we set up shared state for the callbacks: a `responseBuilder` for accumulating text, an `errorRef` for any error message, a `resultJsonRef` for the final JSON result, and a synchronized `collectedArtifacts` list. These use `AtomicReference` and a synchronized list because the A2A SDK invokes our consumers on its own internal callback threads — so we need thread-safe shared state between the main thread and those callbacks.

`conn.client().sendMessage(message, consumers)` kicks off the internal threads that drive the communication with the remote agent. From there, we can use a latch (or similar synchronization primitive) to block the main thread until the task reaches a state we actually need to act on — `input-required`, `completed`, or `failed` — at which point we build and return the final result.

```java
public static String process(AgentConnection conn, Message message) {
    AtomicReference<String> errorRef = new AtomicReference<>();
    AtomicReference<String> resultJsonRef = new AtomicReference<>();
    List<Map<String, Object>> collectedArtifacts = Collections.synchronizedList(new ArrayList<>());

    List<BiConsumer<ClientEvent, AgentCard>> consumers = List.of(
        (event, card) -> {
            if (event instanceof TaskUpdateEvent tue) {
                UpdateEvent ue = tue.getUpdateEvent();
                if (ue instanceof TaskStatusUpdateEvent tsue) {
                    // Handle status updates (working, input required, completed, failed, etc.)
                } else if (ue instanceof TaskArtifactUpdateEvent taue) {
                    // Handle new artifacts produced by the task
                }
            } else if (event instanceof TaskEvent taskEvent) {
                // Handle TaskEvents, provides the final Task when complete
            }
        });

    // Send the message, wait until we need to do something, then build the result and return
    conn.client().sendMessage(message, consumers);
    latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    return buildResult(errorRef, resultJsonRef, collectedArtifacts);
}
```

#### A2A TaskStatusUpdate Event

Zooming in on the `TaskStatusUpdateEvent` branch, this is where we react to the lifecycle states we covered earlier — `WORKING`, `INPUT_REQUIRED`, `COMPLETED`, `FAILED`, and `UNKNOWN`.

The `WORKING` and `INPUT_REQUIRED` states are a little different from the others:

- **`WORKING`** doesn't require us to do anything — it's just the remote agent letting us know it has started processing the task. We keep waiting.
- **`INPUT_REQUIRED`** does require action. The remote agent needs more information from us to continue, so we capture the `taskId` and `contextId` (so our agent can resume this task later by sending a follow-up message via the `A2ATool`), build an `InputRequiredEventData` payload, store it in `resultJsonRef`, and count down the latch.

The `COMPLETED`, `FAILED`, and `UNKNOWN` states all mean the same thing from the perspective of this method: the remote agent is done. We package the final status message as a `FinalEventData`, store it, and count down the latch. From there our local agent loop continues — typically relaying the final result or artifact back to the human user.

Counting down the latch is how we coordinate across threads: the A2A SDK invokes our consumer on its own callback threads, and the main thread is parked on `latch.await()` until one of those callbacks signals "we're done waiting." Once the latch fires, `buildResult` packages everything `resultJsonRef`, `errorRef`, and `collectedArtifacts` have accumulated into a final JSON response.

```java
public static String process(AgentConnection conn, Message message) {
    // Result collection objects defined here...
    List<BiConsumer<ClientEvent, AgentCard>> consumers = List.of((event, card) -> {
        UpdateEvent ue = tue.getUpdateEvent();
        if (ue instanceof TaskStatusUpdateEvent tsue) {
            String statusMsg = A2AHelpers.extractTextFromMessage(tsue);
            if (tsue.getStatus() == TaskState.WORKING) {
                // Nothing to do here, we just need to wait for the Remote Agent to progress
            } else if (tsue.getStatus() == TaskState.INPUT_REQUIRED) {
                InputRequiredEventData data = new InputRrequiredEventData(tsue.getTaskId(), tsue.getContextId(), statusMsg);
                resultJsonRef.set(data.toJSON());
                latch.countDown();
            } else {
                // Use this for COMPLETED, FAILED, and UNKNOWN states, as they all
                // indicate the Remote Agent is done and we should return a final result
                FinalEventData data = new FinalEventData(statusMsg);
                resultJsonRef.set(data.toJSON());
                latch.countDown();
            }
        }
    });

    // Send the message, wait until we need to do something, then build the result and return
    conn.client().sendMessage(message, consumers);
    latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    return buildResult(errorRef, resultJsonRef, collectedArtifacts);
}
```

#### A2A TaskArtifactUpdate Event

The other branch of the `TaskUpdateEvent` consumer handles `TaskArtifactUpdateEvent`s — the actual deliverables the remote agent produces over the life of the task.

The handling here is straightforward: pull the `Artifact` off the event, iterate over its `parts`, and convert each part into a simple map keyed by the artifact's name and the part's content (text for `TextPart`, structured data for `DataPart`). Each artifact map is then appended to the shared `collectedArtifacts` list so it can be included in the final result.

Because remote agents can stream artifacts incrementally (recall `append: true` and `lastChunk` from the artifact structure), this consumer can fire many times for a single task — each invocation just adds another artifact (or chunk) onto the synchronized list.

```java
List<Map<String, Object>> collectedArtifacts = Collections.synchronizedList(new ArrayList<>());

if (ue instanceof TaskArtifactUpdateEvent taue) {
    Artifact artifact = taue.getArtifact();
    Map<String, Object> artifactMap = new HashMap<>();
    if (artifact.parts() != null) {
        for (Part<?> part : artifact.parts()) {
            if (part instanceof DataPart dataPart) {
                artifactMap.put("title", artifact.name());
                artifactMap.put("data", dataPart.getData());
            } else if (part instanceof TextPart textPart) {
                artifactMap.put("title", artifact.name());
                artifactMap.put("text", textPart.getText());
            }
        }
    }
    collectedArtifacts.add(artifactMap);
}
```

#### A2A Tool Result

Once all of the consumers have done their job — appending status updates to `resultJsonRef`, errors to `errorRef`, and artifacts to `collectedArtifacts` — and the task has resolved into one of the terminal states, the latch counts down and `latch.await()` unblocks. At that point the `process` method calls `buildResult` to package everything into a single JSON response.

Because this is ultimately the return value of a tool call, the result needs to be a `String`. We just format some JSON that reflects one of three outcomes:

- We got a real result back — return it as `success` along with any collected artifacts.
- We got an error — return it as `failed` with the error message.
- We got nothing at all — also treated as `failed`, since the tool call still needs a response to hand back to the agent.

That JSON string then bubbles back up through `A2AHandler.process` → `A2ATool.execute` and lands in the local agent's tool-call result, where the agent loop can reason over it and decide what to do next.

```java
public static String buildResult(AtomicReference<String> errorRef, AtomicReference<String> resultJsonRef, List<Map<String, Object>> collectedArtifacts) {
    String resultJson = resultJsonRef.get();
    if (resultJson != null) {
        return gson.toJson(Map.of("status", "success", "message", resultJson, "artifacts", collectedArtifacts));
    }

    String error = errorRef.get();
    if (error != null) {
        return gson.toJson(Map.of("status", "failed", "message", "Error: " + error));
    }

    return gson.toJson(Map.of("status", "failed", "message", "No response received from agent"));
}
```

### A2A Server

The support agent server lives in `support-agent-server/` and follows the same pattern as the [A2A JS SDK samples](https://github.com/a2aproject/a2a-samples/tree/main/samples/js). The key components are:

1. **TaskStore** — `InMemoryTaskStore` tracks task state across requests
2. **AgentExecutor** — implements the `execute()` method where the agent's ReAct loop runs; publishes status updates and artifacts via `ExecutionEventBus`
3. **DefaultRequestHandler** — wires the agent card, task store, and executor together
4. **Express middleware** — `agentCardHandler`, `jsonRpcHandler`, and `restHandler` expose the A2A endpoints

The support agent uses **sentinel tools** (`request_verification`, `request_information`, `offer_resolution_options`) that don't execute a function — instead, they trigger an `input-required` pause in the A2A protocol, waiting for the caller to respond. See `support-agent-server/server.ts` and `support-agent-server/support-tools.ts` for the full implementation.

### Sample Code

- [A2A Protocol Spec](https://github.com/a2aproject/a2a-spec)
- [A2A JS SDK](https://github.com/a2aproject/a2a-js)
- [A2A Java SDK](https://github.com/a2aproject/a2a-java-sdk)
- [A2A Sample Agents](https://github.com/a2aproject/a2a-samples)

## Multi-Agent Communication Implemetation

TODO: Example pulled from out existing source code demo
