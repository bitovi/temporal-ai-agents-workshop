# Exercise 6 - Agent Decisions

## Goals

The goal of this exercise is to understand how different agent decision strategies can be implemented and how they affect the behavior of an LLM-based Agent.

- Reasoning and Acting Agent Architecture - How to structure an agent that can both reason about a problem and take actions (e.g., calling tools, asking clarifying questions) to solve it.

- Model Provider Reasoning Effort - How much reasoning does the model provider (OpenAI, Anthropic, Bedrock, etc) do before returning a text or tool response.

## What you need to know

TODO

### How it works

Take a look at a few different strategies for agent decision making, and how they affect the behavior of the agent.

#### Reasoning and Acting Agent Architecture

As we saw in Exercise 5, we can build an agent workflow that can run multiple iterations of reasoning and acting, allow the model to call tools, collect information, ask clarifying questions, and then generate a final response.

The 'thought' step of this 'thought' 'action' 'observation' loop is where the model can reason about the problem, plan steps to solve it, and determine what actions are needed to work towards a solution.

With Temporal Workflows, Activities, and Signals we can build a flexible agent architecture that can handle complex interactions, maintain state across potentially infinite iterations.

#### Model Provider Reasoning Effort

Many Model Providers such as OpenAI, Anthropic, and Bedrock have arguments in their API that allow you to specify how much reasoning the model should do before returning a response. This can affect how the model decides when to call tools, when to ask clarifying questions, and how it generates its final response.

In some cases, you may want the model to do more reasoning and planning before taking any actions, which can lead to more accurate and useful responses. In other cases, you may want the model to take actions more quickly.

This can be used in combination with the 'thought' step of the ReAct agent architecture to improve the agents performance on complex tasks, by allowing it to do more reasoning before taking actions, and then using the outputs of those actions to inform its next steps.

For other steps, such as 'observation' or context 'compact' steps, we may want to have less reasoning, simply because it is not necessary, and would just add latency to the agent's response time and API costs.

#### Baysian Classifiers

Depending on the specific Agent use-case, sometimes the best answer is to remove some of the decision making from the LLM entirely, and instead use more traditional programming techniques to make decisions.

For example, if we have a specific set of tools that the agent can call, and we want to determine which tool to call based on the user's query, we could use a Bayesian Classifier to classify the user's query into one of several categories, and then map those categories to specific tools. This can be more efficient and cost effective than having the model determine which tool to call, especially if the categories are well defined and the mapping to tools is straightforward.
