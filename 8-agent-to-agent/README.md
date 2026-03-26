# Exercise 8 - Multi-Agent Orchestration

## Goals

The goal of this exercise is to understand how to configure multiple LLM-based agents to collaborate and communicate with each other to solve complex tasks that may require diverse expertise or capabilities.

Implementing agent-to-agent communication allows for the creation of more sophisticated AI systems that can leverage the strengths of different models or specialized agents to achieve better outcomes.

## What you need to know

Single-agent systems have limitations. One single Agent trying to handle research, reasoning, code generation, customer support, billing systems, and validation simultaneously tends to be mediocre at all of them. As the amount of information in the models context grows, quality often degrades. This "context rot" can be mitigated by multi-agent orchestration, which addresses this by breaking down complex tasks across specialized agents, each operating within its own focused context window.

- Specialization over genereralization
  - Each agent can be tuned via system prompts, model selection, and tool definitions for a specific domain.
  - A billing agent doesn't need to understand account verification logic, and vice versa. This mirrors how human teams organize around expertise.

- Parallelization
  - Tasks that don't depend on each other can be executed in parallel. We saw this idea in the Plan & Execute Architecture!
  - While our amount of computation stays the same, we can achieve faster results by running independent tasks concurrently across multiple agents.

- Context window management
  - Each agent gets a fresh context window.
  - A research sub-agent can chew through hundreds of documents without polluting the orchestrator's context.
  - Only the distilled summary flows back up.
  - This is one of the primary reasons tools like GitHub Copilot and Claude Code use sub-agents as part of their architecture, to keep the main conversation clean and focused.

- Fault tolerance
  - If one agent fails or produces an error, the orchestrator can retry the task with a different agent or ask a human for intervention.

The most common practice in these multi-agent systems is to have a top level supervisor or orchestrator agent that then delegates tasks to specialized sub-agents. The orchestrator manages the overall workflow, monitors progress, and handles errors, while the sub-agents focus on their specific tasks.

In our exercise later we will see this pattern in action, with our chat AI Agent interacting with a Customer Support Agent on behalf of a user over the Agent2Agent Protocol.


### Framework vs Protocol




### Real World Example: GitHub Copilot and Claude Code

Both GitHub Copilot and Claude Code are great examples of multi-agent orchestration in a developer tool that many of us now use daily. These tools can spawn sub-agents, each with its own context window, system prompt, tool definitions, and independent execution. The main agent acts as an orchestrator, delegating focused subtasks to these sub-agents and receiving back concise results.

This solves two common problems. The first is the always growing context window. A sub-agent can explore dozens of files, run multiple web searches, or analyze a large codebase without any of that intermediate work accumulating in the main conversation. Only the final summary returns to the parent.

The other is parallel execution. Multiple sub-agents can run simultaneously. For example, when researching a topic, Claude Code might spawn one agent per competitor or one per section of a codebase, then synthesize all results.

Claude Code ships with built-in sub-agents (like Task for general-purpose work and Explore for codebase navigation) but also supports user-defined custom sub-agents configured as Markdown files with YAML frontmatter specifying the agent's description, system prompt, allowed tools, and permission mode. Notably, sub-agents cannot spawn their own sub-agents — this prevents infinite nesting and keeps the architecture manageable.
This pattern — an orchestrator coordinating specialized workers with isolated contexts — is the same fundamental architecture we're using in this exercise. The difference is that Claude Code's sub-agents are all local instances of Claude, while our system uses A2A to communicate across agent boundaries (different runtimes, different languages, potentially different organizations).

# Section 3: Multi-Agent Systems

## 3.1 — Why Multi-Agent?

Transition from the single-agent architectures covered in Sections 1 & 2. Establish the core motivations:

- **Context window management / "context rot"** — Callback to the Section 1 discussion. As tool counts, instructions, and conversation history grow, a single agent's quality degrades. Sub-agents operate in isolated context windows, keeping each agent focused. Only distilled results flow back up. This is arguably the #1 reason production tools like Claude Code and GitHub Copilot use sub-agents.
- **Specialization over generalization** — Each agent can have its own system prompt, model selection, tool definitions, and domain focus. A billing agent doesn't need account verification tools cluttering its context, and vice versa.
- **Parallelization** — Independent subtasks can execute concurrently across multiple agents. Callback to Plan & Execute from Section 1 — the DAG-based task plan naturally maps to parallel agent execution.
- **Fault isolation** — If one agent fails, the orchestrator can retry, substitute, or escalate without crashing the whole workflow.
- **Distributed development** — Different teams can own and maintain individual agents independently, composing them into a larger system with clear API boundaries.

### Real-World Example: Claude Code & GitHub Copilot Sub-Agents

Both tools spawn sub-agents with their own context windows, system prompts, and tool definitions. The main agent acts as an orchestrator — delegating focused subtasks and receiving back concise results. Claude Code ships with built-in sub-agents (Task for general work, Explore for codebase navigation) and supports user-defined custom sub-agents via Markdown files with YAML frontmatter. Notably, sub-agents cannot spawn their own sub-agents — preventing infinite nesting.

---

## Orchestration Patterns

The five foundational patterns that all multi-agent systems map to (or hybridize). These are analogous to distributed systems patterns — the same trade-offs around coordination cost, fault isolation, throughput, and observability apply.

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

### Hybrid Patterns

Most production systems combine patterns. Example: a pipeline for the main flow, but a swarm of 20 gathering agents in the research stage. Or an orchestrator-worker at the top with hierarchical teams underneath.

---

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

### Structured Context Objects vs. Full Conversation Forwarding

- **Structured objects** (200-500 tokens): pass only what the next agent needs. LangGraph's approach.
- **Full conversation forwarding** (5,000-20,000 tokens): every agent sees full history. Simple but expensive.
- **Summarized context**: LLM generates compressed summary at each handoff. 70-90% token reduction but adds latency and information loss.

### Context Isolation as a Feature

Sub-agents getting fresh context windows isn't a limitation — it's the point. A research sub-agent can chew through hundreds of documents without polluting the orchestrator's context. Only the distilled summary flows back up. This is the key architectural insight behind Claude Code's sub-agent model.

---

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

### Comparison Matrix

| Framework          | Orchestration Model         | State Management                  | Language Support     | Best For                                 |
| ------------------ | --------------------------- | --------------------------------- | -------------------- | ---------------------------------------- |
| LangGraph          | Graph-based                 | Checkpointed state channels       | Python, JS           | Complex workflows, orchestrator-worker   |
| OpenAI Agents SDK  | Handoff-based               | Sessions                          | Python, TS           | Lightweight routing, swarm patterns      |
| Google ADK         | Hierarchy + Workflow agents | Session state with key templating | Python, TS, Go, Java | Structured pipelines, parallel execution |
| MS Agent Framework | Graph + Conversation        | Session-based, event-sourced      | Python, C#, Java     | Enterprise, Azure integration            |
| CrewAI             | Role-based teams            | Shared memory                     | Python               | Rapid prototyping, team-oriented tasks   |

---

## The Multi-Agent Trap: Failure Modes & When NOT to Multi-Agent

Critical section — multi-agent isn't always the answer. Google DeepMind research found multi-agent networks can amplify errors 17x. Gartner predicts over 40% of agentic AI projects will be canceled by end of 2027.

- **Start with a single agent** — only go multi-agent when you hit concrete limitations (too many tools, context overflow, need for parallelization)
- **Cascading failures** — one agent's bad output becomes another's bad input. Each handoff is an error amplification point.
- **Coordination overhead** — every additional agent adds latency from routing decisions and context management
- **Observability challenges** — debugging "why did the user end up at Agent F instead of Agent D?" requires production-grade distributed tracing
- **The single-agent ceiling test** — if your agent works well with fewer tools and a focused system prompt, you don't need multi-agent. Refactor the prompt before reaching for orchestration.

---

## 3.6 — Connecting to Temporal

Bridge to your existing Temporal-based architecture from Exercises 1-7. How do these multi-agent patterns map to Temporal's primitives?

- **Orchestrator-Worker** → Temporal Workflow as orchestrator, Activities or Child Workflows as workers
- **Parallel Fan-Out** → `Promise.all()` on multiple Activity invocations
- **Pipeline** → Sequential Activity execution within a Workflow
- **Hierarchical** → Parent Workflows delegating to Child Workflows
- **Handoffs** → Signal-based communication between Workflows
- **State** → Temporal's event-sourced Workflow state replaces framework-specific state management
- **Fault tolerance** → Temporal's built-in retry policies, timeouts, and saga patterns

Advantage of Temporal over framework-built-in orchestration: durable execution, replay, versioning, and observability come for free. You're not reinventing distributed systems plumbing.

---

## 3.7 — Distributed Multi-Agent: The Agent2Agent Protocol

_[Existing A2A content from Exercise 8 goes here]_

Transition: Everything in 3.2-3.6 assumes agents are co-located — same runtime, same process, same organization. A2A extends multi-agent to the distributed case: different runtimes, different languages, potentially different organizations.

- A2A vs MCP positioning (complementary, not competing)
- Agent Cards as service discovery
- Task lifecycle and state machine
- Sentinel tools and input-required pausing
- The A2A + Temporal integration in the exercise

---

## 3.8 — Practical Exercise

The existing Exercise 8 implementation: personal assistant agent (Java/Temporal) communicating with a remote Riot Games support agent (TypeScript) over A2A.

### Potential Extensions / Discussion Topics

- Add a local sub-agent pattern: have the personal assistant spawn a focused sub-agent to summarize the A2A conversation before presenting results to the user
- Implement parallel fan-out: query multiple remote agents simultaneously (e.g., billing agent + account agent) and synthesize results
- Compare: what would this look like with LangGraph Supervisor vs. your Temporal implementation?

## Agent-to-Agent Protocol

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
