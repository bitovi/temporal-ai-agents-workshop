# Tool Calling

Tool Calling refers to a mechanism that enables a Large Language Model (LLM) to interact with external tools, services, or APIs as part of its dialogue or reasoning process. Rather than relying solely on the information encoded in its training data, the LLM can “call” predefined functions or actions to retrieve data, perform computations, or interact with other systems.

## Common Use Cases

- Fetching real-time information (weather, game stats, order status)
- Running calculations or invoking business logic (e.g., support ticket lookups)
- Controlling external systems (sending emails, managing schedules)
- Composing multi-step, agentic workflows (chaining multiple tool calls in a process)

## How It Works

1. Tool Definitions: Developers provide the LLM with a set of available tools/functions, each with a name, description, and a structured input/output schema (often in JSON or a similar format).

2. Prompt & Decision: When a user prompt or question requires information or actions beyond the model’s own knowledge (e.g., “What’s the weather in Seoul right now?”), the LLM recognizes the need to invoke a tool.

3. Structured Output: Instead of returning a regular text response, the LLM outputs a structured request that matches the tool’s schema, specifying which tool to call and with what arguments.

4. Execution: The underlying application logic receives this structured output and triggers the actual function or API call outside of the LLM.

5. Result Integration: The response from the tool is passed back to the LLM, which then incorporates the result into a natural language reply for the user.

## Key Points

- The LLM does not run the tools directly, but generates requests for them.
- Tool calling relies on a clear contract (schema) between the LLM and the tools/functions it can access.
- This makes LLM-powered agents more actionable, reliable, and effective in real-world applications.
