## Goals

The goal of this exercise is to ensure your local development envrionment is successfully set up to accomplish all of the exercises in this workshop. The exercise will explain how to set up the necessary tools, configure environment variables, and verify that the local services are running correctly.

## Prerequisites

1. Docker
   - Download: <https://www.docker.com/products/docker-desktop/>
1. Amazon Java 21 from Coretto
   - Download: <https://docs.aws.amazon.com/corretto/latest/corretto-21-ug/downloads-list.html>
1. Maven
   - Download: https://maven.apache.org/download.cgi
1. VSCode (you can use another IDE, but we have launch configurations set up for VSCode)
   - Download: <https://code.visualstudio.com/>
   - Extensions: ["vscjava.vscode-java-pack"](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack)
1. AWS Access Keys (see below)
   - You will need to provide your own AWS Access Key, AWS Secret Access Key, AWS Session Token, and AWS Region that has the necessary permissions and resources for this workshop.
1. AWS Bedrock Memory Id and Region
   - This resource has been provisioned for you prior to the workshop.
1. Postgres
   - Download: <https://www.postgresql.org/download/>
1. Brave Search API Key (Optional)
   - This is an optional API Key, and is not used explicitly during the exercises in this workshop, but is helpful for running more open-ended queries with the AI Agents.
   - Ask the workshop organizers for a Brave Search API Key if you want to use it.

## Environment Variables

You will need to create a `.env` file in the root of the repository with the necessary environment variables.

Copy the `.env.example` file to `.env` and fill in the values at the top of the file that are marked as `todo`.

Each of the different exercises will use the environment variables defined in the root `.env` file. The `.env` file from the root will be automatically copied into each exercise directory when building or running the exercises.

If for some reason the `.env` file is not copied into the exercise directories, you can manually trigger the copy script by running the **Sync Environments** task in VSCode.

## External Systems

There are a few external systems being used by the exercises in this repo:

1. AWS Bedrock - used to host the AI models (one Large Language Model and one Embeddings Model)
1. AWS AgentCore Memory - used to store the memory of the AI agents
1. AWS S3 - used for document storage. We'll be storing policy and rubric documents here, and also using it for temporary storage.
1. Postgres - will be used for structured data storage. We'll be storing extracted ticket data.

## Local Systems / Mocks

All other systems will be run locally using the **Docker Compose Up** task.

1. Temporal - orchestration layer for AI workflows
1. Qdrant - vector database for storing embeddings for use in Retrieval Augmented Generation
1. Postgres - relational database for structured data storage
1. Agent Chat Server - hosts a Web UI for interacting with the AI agents
1. Mock MCP Server - used to test the Model Context Protocol in exercise 4
1. Support Agent Server - used for agent-to-agent communication in exercise 8

To stop and clean up the running services, use the **Docker Compose Down** task.

You can access the VSCode 'Run Task' menu by pressing `Cmd+Shift+P` (Mac) or `Ctrl+Shift+P` (Windows/Linux) and typing "Run Task".
![image](../.images/vscode-cmd-menu.png)

Select the appropriate task from the list to run.

![image](../.images/vscode-run-task.png)

## VSCode Launch Configurations

Each Exercise has (at least) two launch configurations configured:

1. The first to start the Temporal Worker
1. The second to use a Temporal Client to start a Workflow

The Launch Configurations can be accessed from the Run and Debug view in VSCode:

![image](../.images/vscode-run-menu.png)

In order to verify that your environment is set up correctly, you should run the `Exercise 0 - Worker` and `Exercise 0 - Client` launch configurations in VSCode and check the Temporal UI to ensure the workflow executed successfully.

You should see the workflow execute successfully in the Temporal UI.

![image](../.images/temporal-exercise-0.png)

## Solution

To recap, here are all the steps needed to verify your environment is set up correctly:

1. Set up your `.env` file with the necessary environment variables
1. Start the local services with the **Docker Compose Up** task
1. Launch `Exercise 0 - Worker`
1. Launch `Exercise 0 - Client`
1. Visit the [Temporal UI](http://localhost:8233) and verify the workflow executed successfully
