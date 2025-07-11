# Exercise 3 - Tool Calling

## Goals

The goal of this exercise is to understand how to configure an LLM to call external tools, such as APIs, query datbases, or perform calculations, to enhance its capabilities and to provide more accurate and useful responses to user queries.

In this exercise, by registering tools with the LLM, and then posing a question to the model, we can allow the model to determine when it needs to call an external tool to fetch information or perform a task that is otherwise outside of its text-based capabilities.

## What you need to know

Tool Calling, also called Function Calling or Tool Use, is a technique that allows LLMs to perform a wider variety of tasks. This technique comes with some other key advantages when building applications with LLMs:

- LLMs can retrieve real-time data (e.g., current weather, live news, stock prices) by calling web search tools or specific APIs, addressing the limitation of their training data cutoff
- LLMs can automate tasks by interfacing with productivity tools (e.g., sending emails, reading/writing calendar entries, scheduling meetings, processing payments)
- LLMs can use code execution tools (like Python interpreters) to perform accurate mathematical or logical operations that they are not inherently good at, such as calculating compound interest or performing statistical analysis
- LLMs can orchestrate multiple function calls to solve multi-step problems (e.g., planning a trip by checking flight availability, booking a hotel, and renting a car through different APIs). This allows them to construct novel workflows and combine tools in creative ways
- By allowing the LLM to control function invocation, it can reliably extract and format parameters from user input for APIs, even with less controlled inputs

## How it works

In order for the model to determine what tools might be useful to it, and what parameters are required to call those tools, we need to define the tools and their input parameters in a way that the model can understand. This is typically done by providing a JSON schema that describes the tool's name, description, and the parameters it accepts.

Each model provider, in our case Bedrock, has its own way of defining tools and their schemas. Many of them use JSONSchema, which is a standard way to describe the structure of JSON data. This allows the model to understand what inputs are required for each tool and how to format the output.

A `ToolSpecification` for Bedrock contains the following fields:

- `name`: The name of the tool, which is used to identify it when calling the tool.
- `description`: A brief description of what the tool does, which helps the model understand when to use it.
- `inputSchema`: A `ToolInputSchema` describes the input parameters required by the tool. This includes the type of each parameter, if it is required or optiona, and any additional constraints on the format.

## Solution
