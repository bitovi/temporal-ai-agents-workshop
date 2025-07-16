
# Exercise 1 - Prompt Engineering

## Goals

The goal of this first exercise is to learn the best practices of prompt engineering. We will learn about these best practices and apply them to guide an LLM to evaluate a customer service agent's response to a customer's question based on a series of guidelines.

## What you need to know

Each LLM has its own set of guidelines for how to optimize your text prompts to get the highest quality responses. [Bedrock](https://docs.aws.amazon.com/bedrock/latest/userguide/prompt-engineering-guidelines.html) has a good list of each model's prompt guides. For this workshop, we will use Claude 3.7 Sonnet, so you can refer to [Anthropic's guide](https://docs.anthropic.com/en/docs/build-with-claude/prompt-engineering/overview). Anthropic also has an [interactive guide](https://github.com/anthropics/prompt-eng-interactive-tutorial/tree/master/AmazonBedrock/anthropic) that you can try on your own.

### Parts of a Prompt

1. The task you want the LLM to perform
2. Context of the task
3. Examples
4. Input text for the LLM to use in its response

### Techniques

#### Be clear and direct

#### Use examples

#### Use Chain of thought prompting

#### Use XML tags

#### Long Prompts

#### Use a system prompt

### Formatting Prompts for Bedrock SDK