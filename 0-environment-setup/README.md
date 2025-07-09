
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

The only _external_ system being used in this workshop is *AWS Bedrock*, which will host the AI models being used throughout the exercises.

To correctly connect to Bedrock, copy the `config.properties-example` file to `config.properties` and replace the `TODO` property.

## Local Systems / Mocks

All other systems will be run locally using `docker compose`. This includes a mock of the Zendesk API, which will be used to retrieve some sample ticket data.

1. Temporal - orchestration layer for AI workflows
1. Qdrant - being used as a vector database for storing embeddings for use in Retrieval Augmented Generation
1. Postgres - being used to store structured data
1. Zendesk API - being used to retrieve documents in order to supply them as context to AI systems

To start these, run the `up.sh` script in the root of the repo. There is also a `down.sh` script to tear everything down.

## VSCode Launch Configurations

Each Exercise has two launch configurations configured:

1. The first to start the Temporal Worker
1. The second to use a Temporal Client to start a Workflow

![image](https://github.com/user-attachments/assets/95e269c7-18fc-4b25-aee5-9bac5b78288e)

After making code changes, be sure to restart the worker:

![image](https://github.com/user-attachments/assets/3d4a47a2-f65b-403a-ab0c-9bb4d8a02bbc)

## Solution

![image](https://github.com/user-attachments/assets/4d497e90-f245-4108-8f72-03ca1c9602e7)
