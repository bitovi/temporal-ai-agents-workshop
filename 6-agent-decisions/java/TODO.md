# Agent Decisions

## Part A - Initial Example

First let's run the existing implementation of a ReAct ("Reasoning and Acting") Agent.

1. Run Task: Docker Compose Down
2. Run Task: Docker Compose Up
3. Launch: Exercise 6 - Worker
4. Launch: Exercise 6 - Client

Let's open the latest workflow in the [temporal ui](http://localhost:8233/) so we can observe that behavior of the agent.

Notice how the workflow loops through the THOUGHT, ACTION, and OBSERVATION activities.

Click on individual activities to inspect the input and output of each step.

Notice how the result for each activity includes a `usage.reasoningTokens` value.
This value reflects the number of tokens that the model spent while performing it's own internal reasoning before returning a response.
Note that the workflow result includes it's own `usage.reasoningTokens` value which represents the sum of all the activities' reasoning tokens.

## Part B - Experiment
Search for `TODO_DECISIONS` in the code:

- TODO_DECISIONS: Experiment with changing the max reasoning effort (low, medium, high)
- TODO_DECISIONS: Experiment with different word problem prompts