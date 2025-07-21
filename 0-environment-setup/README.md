
# Exercise 0 - Environment Setup

## Goals

The goal of this exercise is to ensure your local development envrionment is successfully set up to accomplish all of the exercises in this workshop.

## What you need to know

## Prerequisites

1. Docker
1. Java
1. Maven
1. VSCode (you can use another IDE, but we have launch configurations set up for VSCode)
1. AWS Access Keys (see below)

## External Systems

There are a few external systems being used by the exercises in this repo:

1. AWS Bedrock - used to host the AI models
1. AWS S3 - used for document storage
1. Postgres - used for structured data storage

To correctly connect to these, copy the `config.properties-example` file to `config.properties` and replace the `TODO` property.

## Local Systems / Mocks

All other systems will be run locally using `docker compose`.

1. Temporal - orchestration layer for AI workflows
1. Qdrant - being used as a vector database for storing embeddings for use in Retrieval Augmented Generation
1. An MCP Server - used to test the Model Context Protocol in exercise 4

These will start automatically when you launch `Exercise 0 - Worker` (see below).

## VSCode Launch Configurations

Each Exercise has two launch configurations configured:

1. The first to start the Temporal Worker
1. The second to use a Temporal Client to start a Workflow

![image](https://github.com/user-attachments/assets/95e269c7-18fc-4b25-aee5-9bac5b78288e)

After making code changes, be sure to restart the worker:

![image](https://github.com/user-attachments/assets/3d4a47a2-f65b-403a-ab0c-9bb4d8a02bbc)

## Solution

When you have the `config.properties` set up correctly and launch `Exercise 0 - Worker` and then `Exercise 0 - Client` you should be able to visit the [Temporal UI](http://localhost:8233) and see the workflow executed successfully:

![image](https://github.com/user-attachments/assets/4d497e90-f245-4108-8f72-03ca1c9602e7)
