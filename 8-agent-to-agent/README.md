# Exercise 8 - Multi-Agent Orchestration

## Goals

The goal of this exercise is to understand how to configure multiple LLM-based agents to collaborate and communicate with each other to solve complex tasks that may require diverse expertise or capabilities.

Implementing agent-to-agent communication allows for the creation of more sophisticated AI systems that can leverage the strengths of different models or specialized agents to achieve better outcomes.

## What you need to know

Single-agent systems have limitations. One single Agent trying to handle research, reasoning, code generation, customer support, billing systems, and validation simultaneously tends to be mediocre at all of them. As the amount of information in the models context grows, quality often degrades.

This can often be mitigated by multi-agent orchestration, which addresses this by breaking down complex tasks across specialized agents, each operating within its own focused context window

### Specialization Over Generalization

By specializing agents for their specific tasks, we can achieve higher quality results. Each agent can focus on a narrow domain, with a clear understanding of its responsibilities and limitations.

Each agent can be tuned via system prompts, model selection, and tool definitions for a specific domain.

For example, a billing agent doesn't need to understand account verification logic, and vice versa. This mirrors how human teams organize around expertise.

Sometimes we DO expect our humans, or agents, to generalize across domains, but this is usually less efficient and can lead to lower quality results compared to specialized agents.

### Parallelization

When we have multiple agents working on different parts of a task, we can take advantage of parallelization to speed up the overall process. We saw this general idea in the Plan & Execute Architecture!

This doesnt do anything to increase or decrease the total amount of computation required for a task, but it allows us to complete the task faster by leveraging multiple agents to work concurrently.

### Context Window Management

Context Windows have come up in every exercise so far, as they are a critical factor in determining how much information an agent can consider at once. Proper management of context windows is essential for maintaining the quality and relevance of the agent's responses.

Splitting tasks across multiple agents allows each agent to operate within its own context window, preventing the main orchestrator's context from becoming overloaded.

Each agent gets its own context window, allowing it to process information independently without affecting the main orchestrator's context. Only a final distilled summary flows back up to the orchestrator agent.

> Note: We can see this technique in action in tools like GitHub Copilot and Claude Code, where sub-agents are used to manage context windows effectively! Allowing the main agent to stay focused on the high-level plan, leaving the exploration of the codebase, research, and other detailed tasks to the sub-agents.

If one of the sub-agents fails or produces an error, the orchestrator can handle the situation gracefully without affecting the overall task or polluting the main context window with error messages or incomplete information.

## Framework vs Protocol

There are two main approaches to building multi-agent systems: frameworks and protocols.

Framework level approaches often implement an orchestrator that manages multiple sub-agents, handling task delegation, context management, and error handling. These agents run, often, within the same runtime environment and can share resources directly. The sub-agents are typically tightly coupled with the orchestrator and are not designed to operate independently outside of the framework.

The other approach is protocol-based. Here we allow our agent to communicate with external agents that may be running in entirely different runtime environments, possibly written in different programming languages, and managed by different organizations. We will look more at the Agent2Agent Protocol later in this section.

## Frameworks & SDKs

Survey of the major multi-agent frameworks, their philosophies, and when to use each. Focus on architectural differences rather than API tutorials.

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

- Launched at Google Cloud NEXT 2025, open-sourced
- Workflow agents: `SequentialAgent`, `ParallelAgent`, `LoopAgent` — deterministic, no LLM needed for orchestration
- `LlmAgent` transfer for dynamic routing
- Agent hierarchy with parent/sub-agent tree structure (single parent rule)
- `CustomAgent` via `BaseAgent` extension for arbitrary orchestration logic
- Python, TypeScript, Go, and Java SDKs
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

The most widely deployed pattern in production. A central orchestrator receives tasks, decomposes them, routes subtasks to specialized workers, and aggregates results. Workers don't communicate with each other — all coordination flows through the orchestrator.

- Orchestrator maintains global state, handles error recovery
- Workers are stateless and focused on a single capability
- Trade-off: orchestrator is a single point of failure and potential bottleneck
- Examples: LangGraph Supervisor, AutoGen group chat with selector agent

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

Like any distributed system, multi-agent architectures introduce complexity that can outweigh their benefits if not carefully managed. Most projects should always start with a single agent and only move to a multi-agent setup when there are clear, unavoidable limitations that a single agent cannot address.

There are several failure modes to be aware of:

- **Cascading failures**
  - One agent's bad output becomes another's bad input.
  - Each handoff is an error amplification point.

- **Coordination overhead**
  - Every additional agent adds latency from routing decisions and context management

- **Observability challenges**
  - Debugging "why did the user end up at Agent F instead of Agent D?" requires production-grade distributed tracing

- **The single-agent ceiling test**
  - If your agent works well with fewer tools and a focused system prompt, you don't need multi-agent. Refactor the prompt before reaching for orchestration.

## Practical Examples

Claude Code & GitHub Copilot both make use of multi-agent architectures to manage complex tasks.

Both tools spawn sub-agents with their own context windows, system prompts, and tool definitions. The main agent acts as an orchestrator, delegating focused subtasks and receiving back concise results.

Claude Code ships with built-in sub-agents

- 'General Purpose' Agent for general work
- 'Explore' Agent for codebase navigation
- 'Plan' Agent that acts as a software architect, building implementation plans
- 'claude-code-guide' Agent for assisting with using Claude Code itself, documentation, and usage
- 'Verification' Agent, an adversarial agent that attempts to break implementations and find edge cases

Each of these sub-agents are specialized for a particular type of task, allowing the main orchestrator agent to delegate work efficiently and maintain a clean separation of concerns.

Simple Sub-Agents are spawned using this Tool Call:

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

More complex multi-step Agents can also be created:

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

Bridge to your existing Temporal-based architecture from Exercises 1-7. How do these multi-agent patterns map to Temporal's primitives?

Temporal solves a lot of problems around normal distributed systems concerns like state management, fault tolerance, and orchestration. We can take advantage of these features when designing multi-agent systems as well, no need to reinvent the wheel when we get these for free from Temporal.

- **Orchestrator-Worker**
  - Temporal Workflow as orchestrator
  - Activities or Child Workflows as workers
- **Parallel Fan-Out**
  - `Promise.all()` on multiple Activity invocations
- **Pipeline**
  - Sequential Activity execution within a Workflow
- **Hierarchical**
  - Parent Workflows delegating to Child Workflows
- **Handoffs**
  - Signal-based communication between Workflows
- **State**
  - Temporal's event-sourced Workflow state replaces framework-specific state management
- **Fault tolerance**
  - Temporal's built-in retry policies, timeouts, and usage of saga patterns

## The Agent2Agent Protocol

The Agent-to-Agent Protocol is an open standard created by Google that enables AI Agents to seamlessly communicate and collaborate with each other in a structured way. The A2A Protocol is now managed by the Linux Foundation. Under the Linux Foundation’s governance, the hope is that A2A will remain vendor neutral, emphasize inclusive contributions and continue the protocol’s focus on extensibility, security and real-world usability across industries.

A2A defines standard 'agent cards', authentication and authorization mechanisums for controlling access between agents. It also provides the ability for agents to collaborate on long-running tasks without exposing their internal state to each other.

In this exercise, our personal assistant agent (Java/Temporal) uses the [A2A Java SDK](https://github.com/a2aproject/a2a-java-sdk) as a client to communicate with a remote Riot Games support agent (TypeScript) built with the [A2A JS SDK](https://github.com/a2aproject/a2a-js).

### How it works

The A2A protocol follows a client-server model. Our personal assistant is the A2A **client** and the support agent is the A2A **server**.

The Agent2Agent protocol consists of several building blocks for agent interactions:

- A2A client (client agent)
  - The A2A client, also known as the client agent, can be an app, service or other AI agent that delegates requests to remote agents. It uses the Agent2Agent protocol to initiate communication.

- A2A server (remote agent)
  - The A2A server, also called the remote agent, takes requests, processes tasks and responds with status updates or results. It exposes an HTTP endpoint that’s compatible with the Agent2Agent protocol.

- Agent card
  - This JSON file outlines agentic AI metadata and can be accessed using a URL. It contains basic information about an agent, including its name, description, version, service endpoint URL, supported modalities or data types and authentication requirements.
  - Agent cards are similar to model cards for large language models (LLMs). They also advertise an agent’s capabilities and skills, serving as a business card, résumé or LinkedIn profile that allows agents to discover each other.

- Task
  - A task represents a unit of work needed to accomplish a request. It has a unique ID and progresses through a lifecycle of defined states (submitted, working, input-required, completed, failed). Tasks are useful for multi-turn processing or long-running agent-to-agent collaboration.
- Message
  - As a fundamental unit of communication, a message depicts a single exchange or turn in a conversation. It contains one or more parts holding the actual content.

  - Messages relay answers, context, instructions, prompts, questions, replies and status updates. Depending on the sender, each message has an associated role, which can either be an agent role for server-sent messages or a user role for client-sent messages.

- Artifact
  - An artifact is a tangible product generated by the A2A server as a result of its work. It can be a document, image, spreadsheet or any other deliverable. Like messages, artifacts consist of one or more parts and can be incrementally streamed.

- Part
  - A part is a piece of content inside a message or an artifact. Parts have various types based on the data they carry. A TextPart is a vessel for text, a FilePart represents files and a DataPart encompasses structured JSON (JavaScript Object Notation) data.

The A2A protocol follows a client-server model setup with a three-step workflow:

- Discovery
- Authentication
- Communication

Communication starts with a client agent sending a task to the chosen remote agent. Agent-to-agent communication occurs over HTTPS for secure transport, with JSON-RPC (Remote Procedure Call) 2.0 as the format for data exchange.

The remote agent then processes the task. If it requires more information, it notifies the client agent asking for additional details. Once it completes the task, the remote agent sends a message to the client agent along with any generated artifacts.

A2A also provides task management features for more complex tasks that can’t be completed immediately, such as those needing human intervention or involving multiple steps. In the case of long-running tasks that take hours or days or if a client agent gets disconnected, the A2A protocol allows for asynchronous updates through push notifications sent to a secure client-supplied webhook. For large or long outputs or continuous status updates, the A2A protocol supports real-time streaming using server-sent events (SSE).

### MCP vs A2A

Previously introduced by Anthropic in 2024, the Model Context Protocol (MCP) serves as a standardization layer for AI applications to communicate effectively with external services, such as APIs (application programming interfaces), data sources, predefined functions and other tools. Meanwhile, the A2A protocol focuses on agent collaboration, facilitating communication between AI agents.

Both protocols are meant to complement each other. For example, a retail store might have its own inventory agent that uses MCP to interact with databases storing information about products and stock levels. If the inventory agent detects products low in stock, it notifies an internal order agent, which then uses A2A to communicate with external supplier agents and place orders.

### Agent Card

The support agent in this exercise advertises itself via an agent card at `.well-known/agent-card.json`. You can see the card definition in `support-agent-server/server.ts`. Here's what it looks like:

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
