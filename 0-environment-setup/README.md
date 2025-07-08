
# Exercise 0 - Environment Setup

## Goals

The goal of this exercise is to ensure your local development envrionment is successfully set up to accomplish all of the exercises in this workshop.

## What you need to know

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

## Solution
