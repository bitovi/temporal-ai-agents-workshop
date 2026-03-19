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

### Real World Example: GitHub Copilot and Claude Code

Both GitHub Copilot and Claude Code are great examples of multi-agent orchestration in a developer tool that many of us now use daily. These tools can spawn sub-agents, each with its own context window, system prompt, tool definitions, and independent execution. The main agent acts as an orchestrator, delegating focused subtasks to these sub-agents and receiving back concise results. 

This solves two common problems. The first is the always growing context window. A sub-agent can explore dozens of files, run multiple web searches, or analyze a large codebase without any of that intermediate work accumulating in the main conversation. Only the final summary returns to the parent.

The other is parallel execution. Multiple sub-agents can run simultaneously. For example, when researching a topic, Claude Code might spawn one agent per competitor or one per section of a codebase, then synthesize all results.

Claude Code ships with built-in sub-agents (like Task for general-purpose work and Explore for codebase navigation) but also supports user-defined custom sub-agents configured as Markdown files with YAML frontmatter specifying the agent's description, system prompt, allowed tools, and permission mode. Notably, sub-agents cannot spawn their own sub-agents — this prevents infinite nesting and keeps the architecture manageable.
This pattern — an orchestrator coordinating specialized workers with isolated contexts — is the same fundamental architecture we're using in this exercise. The difference is that Claude Code's sub-agents are all local instances of Claude, while our system uses A2A to communicate across agent boundaries (different runtimes, different languages, potentially different organizations).

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
