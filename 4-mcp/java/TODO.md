# Model Context Protocol (MCP) Workflow

This exercise provides a simple implementation of a Model Context Protocol (MCP) workflow using Java and Temporal.

The workflow for this exercise is nearly identical to the Tool Calling workflow. However, instead of registering our tools manually, we will use the MCP to automatically discover and register tools.

First take a look at the `McpWorkflowImpl.java` file. The `execute` method is where the workflow logic is defined. The workflow uses a chat history to interact with an AI model, which can call tools based on the conversation context.

You should run the provided Worker and Client first to see the default behavior.

Run the workflow without modiications first by using the vscode launch configuration for 'Exercise 2 - Worker' and then 'Exercise 2 - Client'. This will start the Temporal worker and client, allowing you to see how the workflow executes with the provided chat history.

Take a look at the Temporal Web UI to observe the Workflow executions. You can access it at:
<http://localhost:8233/namespaces/default/workflows>

Look for places with `TODO_MCP` comments in the code to find areas that you might want to examine and compare to the previous Tool Calling exercise.

Once you have run the default implementation, take a look at the sample MCP Server provided in the `mock-mcp-server-ts/server.js` file. This server is run automatically as part of the Docker Compose setup.

The MCP Server provides a single Weather Tool with the same behavior as the hardcoded tool in the previous exercise.
