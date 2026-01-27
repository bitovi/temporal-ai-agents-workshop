We need to implement an agentic loop using the aws converse api.

This agent will be living inside the a2a (agent to agent) server here: book-agent-server/server.ts

This should be as simple as possible.

If successful, a client should be able to interact with the a2a server.
The server implementation (the agentic loop) will call on a bedrock llm using the converse api.
the server will respond to the client.

this should be a synchronous request/resoponse interaction with the a2a server (not async task)
