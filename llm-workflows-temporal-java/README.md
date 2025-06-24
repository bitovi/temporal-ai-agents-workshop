# LLM Temporal Workflows Java SDK

The repository contains a few sample projects for using LLMs with Temporal using the Temporal Java SDK along with several non-Temporal examples demonstrating different AI features, libraries, or technologies that would be useful for building out a more complete examples.

## Demos and Workflows

All of the demos are set up to run with vscode `launch.json` if you have the Java support extensions installed.
Language Support for Java(TM) by Red Hat.

### AWS Bedrock Demos

- AWS Bedrock Embedding Model
- AWS Bedrock Structured Output
- AWS Bedrock Tool Calling

### Other Demos

- Model Context Protocol
- Ollama Tool Calling

### Temporal Workflows

- Basic Chat Workflow
- Agent Goal Setting Workflow
  - Based on <https://github.com/temporal-community/temporal-ai-agent>

## Setup and Development

Run `docker compose up` to start the dependencies:

- postgresql
- temporal
- qdrant
- localstack
- temporal-worker
- langfuse
- redis

Environment Variables are required to connect to AWS Bedrock. You can get these by going to the Bitovi AWS access portal
and grabbing the `sandbox` access keys for `AWS access key ID`, `AWS secret access key`, and `AWS session token`. These can be updated in the `llm-workflows-temporal-java/config.properties` file. There is a `llm-workflows-temporal-java/config.properties.example` that shows the general shape of the config.

The `Demos` are standalone and can be executed from the `launch.json`.

For the Temporal workflows, once all the dependencies are running, you can use the `init` and `send message` from the `launch.json` to kick off the workflow, prompting you for a name (workflow-id) and the `send message` to signal an additional message into the chat workflow.

For the Agent Goal Workflow the easiest way to interact with it is to use the `BitoviAgentWorkflowUI` demo listed as "Agent Workflow Demo Interface" which will open a Java Swing UI that presents as a basic chat UI. This will start a Workflow automatically and will signal your chat messages into the Workflow along with querying for the LLM responses. The Workflow will also be signaled to close if the UI is closed.

If code changes are made and the Temporal Worker needs to be restarted/updated make sure to run `docker compose up --build worker` or the `worker.sh` shell script. A single Worker supports all of the workflows.

In order to build and run manually, without using the vscode launch.json, you can use `mvn compile` to build the project and then `mvn exec:java -Dexec.mainClass="bitovi.BitoviAgentWorkflowUI"` to launch the chat UI. Replace `BitoviAgentWorkflowUI` with whatever main class or demo name you want to execute. Recommend seeing the `launch.json` to get the main class names.
