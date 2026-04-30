# Exercise 3 - Tool Calling

## Goals

Learn how to configure an LLM to call external tools—such as APIs, database queries, or calculations—so it can answer queries that go beyond its text-based capabilities. You'll register a tool with the model, prompt it, and let it decide when to invoke that tool.

## What you need to know

Tool Calling (also called Function Calling or Tool Use) lets LLMs do far more than generate text:

- Retrieve real-time data (weather, news, stock prices) via APIs, bypassing training-data cutoffs
- Automate tasks through productivity tools (email, calendars, payments)
- Delegate math and logic to code execution tools (e.g., a Python interpreter) for reliable results
- Orchestrate multi-step workflows by chaining or parallelizing tool calls
- Reliably extract and format parameters from unstructured user input

## How it works

To call a tool, the model needs a machine-readable description of it. Each provider has its own format; Bedrock (used here) uses JSONSchema-based definitions.

A Bedrock `ToolSpecification` contains:

- `name` — identifier used when invoking the tool
- `description` — tells the model when the tool is useful
- `inputSchema` — a `ToolInputSchema` describing parameter types, required fields, and constraints

Tools are passed to the model in the `toolConfig` of a `ConverseRequest`. Instead of returning text, the model may return a `ToolUseBlock` containing the tool name and parameters. Your application is responsible for validating the tool exists, executing it, and sending the result back to the model as context for the next request.

This exercise provides a single tool that fetches the current weather for a location, and the wrapper allows only one tool call. A production implementation would typically loop—handling multiple (and possibly parallel) tool calls and errors—until the model produces a final response. As with RAG, all tool outputs become part of the context the model uses to craft that response.
