# Exercise 0 - Environment Setup

## Goals

The goal of this exercise is to ensure your local development envrionment is successfully set up to accomplish all of the exercises in this workshop. The exercise will explain how to set up the necessary tools, configure environment variables, and verify that the local services are running correctly.

## What you need to know

## Prerequisites

1. Docker
   - Download: <https://www.docker.com/products/docker-desktop/>
1. Amazon Java 21 from Coretto
   - Download: <https://docs.aws.amazon.com/corretto/latest/corretto-21-ug/downloads-list.html>
1. Maven
   - Download: https://maven.apache.org/download.cgi
1. VSCode (you can use another IDE, but we have launch configurations set up for VSCode)
   - Download: <https://code.visualstudio.com/>
1. AWS Access Keys (see below)
    - You will need to provide your own AWS Access Key, AWS Secret Access Key, AWS Session Token, and AWS Region that has the necessary permissions and resources for this workshop.

## Environment Variables

Copy the `.env.example` file to `.env` and fill in the `todo` values.

Each exercise has its own copy of the `.env` file.
After creating or updating the root `.env` file, run the **Sync Environments** task in VSCode to copy it into all of the exercise directories.
**You need to do this each time you make changes to the root `.env` file.**

## External Systems

There are a few external systems being used by the exercises in this repo:

1. AWS Bedrock - used to host the AI models
1. AWS S3 - used for document storage
1. Postgres - used for structured data storage

## Local Systems / Mocks

All other systems will be run locally using the **Docker Compose Up** task.

1. Temporal - orchestration layer for AI workflows
1. Qdrant - vector database for storing embeddings for use in Retrieval Augmented Generation
1. Postgres - relational database for structured data storage
1. Agent Chat Server - web UI for interacting with the AI agent
1. Mock MCP Server - used to test the Model Context Protocol in exercise 4
1. Book Agent Server - used for agent-to-agent communication in exercise 8

To stop and clean up the running services, use the **Docker Compose Down** task.

## VSCode Launch Configurations

Each Exercise has two launch configurations configured:

1. The first to start the Temporal Worker
1. The second to use a Temporal Client to start a Workflow

![image](https://github.com/user-attachments/assets/95e269c7-18fc-4b25-aee5-9bac5b78288e)

After making code changes, be sure to restart the worker:

![image](https://github.com/user-attachments/assets/3d4a47a2-f65b-403a-ab0c-9bb4d8a02bbc)

## Solution

To recap, here are all the steps needed to verify your environment is set up correctly:

1. Set up your `.env` file and run the **Sync Environments** task
1. Start the local services with the **Docker Compose Up** task
1. Launch `Exercise 0 - Worker`
1. Launch `Exercise 0 - Client`
1. Visit the [Temporal UI](http://localhost:8233) and verify the workflow executed successfully:

![image](https://github.com/user-attachments/assets/4d497e90-f245-4108-8f72-03ca1c9602e7)
