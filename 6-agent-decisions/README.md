# Exercise 6 - Agent Decisions

## Goals

The goal of this exercise is to understand how different agent decision strategies can be implemented and how they affect the behavior of an LLM-based Agent.

- Reasoning and Acting Agent Architecture - How to structure an agent that can both reason about a problem and take actions (e.g., calling tools, asking clarifying questions) to solve it.

- Plan and Execute Agent Architecture - How to structure an agent that first generates a complete plan for solving a problem, and then executes that plan step by step.

- Model Provider Reasoning Effort - How much reasoning does the model provider (OpenAI, Anthropic, Bedrock, etc) do before returning a text or tool response.

## What you need to know

TODO

### How it works

Take a look at a few different strategies for agent decision making, and how they affect the behavior of the agent.

#### Reasoning and Acting Agent Architecture

As we saw in Exercise 5, we can build an agent workflow that can run multiple iterations of reasoning and acting, allow the model to call tools, collect information, ask clarifying questions, and then generate a final response.

The 'thought' step of this 'thought' 'action' 'observation' loop is where the model can reason about the problem, plan steps to solve it, and determine what actions are needed to work towards a solution.

With Temporal Workflows, Activities, and Signals we can build a flexible agent architecture that can handle complex interactions, maintain state across potentially infinite iterations.

#### Plan and Execute Agent Architecture

Another common agent architecture is the 'plan and execute' architecture, where the model first generates a complete plan for how to solve the problem, and then executes that plan step by step. This can be useful for tasks that require a lot of planning and coordination, but it can also be less flexible than the 'reasoning and acting' architecture, as it may not allow for as much adaptability and responsiveness to new information or changing circumstances.

The core difference is who decides what to do next. In your ReAct loop, the `thoughtActivity` decides the next action on every iteration with a one-step-at-a-time approach.
In Plan-and-Execute, a `planActivity` generates the full sequence of steps upfront, and then the workflow just iterates through them mechanically.

```ts
type PlanExecuteStep = "IDLE" | "PLANNING" | "EXECUTING" | "RESPONDING";

interface Plan {
  goal: string;
  steps: PlanStep[];
}

interface PlanStep {
  stepNumber: number;
  description: string; // what this step should accomplish
  toolName: string; // which tool to call
  toolInput: ActionInput; // parameters for the tool
  dependsOn: number[]; // which previous steps this needs results from
}

interface StepResult {
  stepNumber: number;
  output: string;
  success: boolean;
}
```

The Activities interface stays similar to our example ReAct Workflow, but swaps `thoughtActivity` and `observationActivity` for planning-specific ones:

```ts
interface Activities {
  // NEW: generates the full plan from the user's query + available tools
  planActivity(context: string[], availableTools: Tool[]): Plan;

  // SAME as the existing actionActivity
  actionActivity(toolName: string, input: ActionInput): string;

  // NEW: after all steps run, synthesize a final answer
  respondActivity(context: string[], plan: Plan, results: StepResult[]): string;

  // NEW (optional): revise the plan when a step fails or results change things
  replanActivity(
    context: string[],
    originalPlan: Plan,
    completedResults: StepResult[],
    failedStep: PlanStep,
    error: string,
  ): Plan;
}
```

The Workflow itself is where the structural difference really shows. In our existing ReAct Workflow, the loop is driven by the LLMs decisions each iteration. In Plan-and-Execute, the LLM runs once to plan, then execution is just tool calls, then the LLM runs once more to synthesize. For a 5-step task, ReAct might make 10+ LLM calls while Plan-and-Execute makes 2-3.

The actual execution loop is normal deterministic code, just iterating over the plan steps:

```ts
let plan = await planActivity(context, availableTools);

for (const planStep of plan.steps) {
  // Check if dependencies are met
  const depsOk = planStep.dependsOn.every(
    (dep) => results.find((r) => r.stepNumber === dep)?.success,
  );

  if (!depsOk) {
    // A dependency failed -- replan from here
    plan = await replanActivity(
      context,
      plan,
      results,
      planStep,
      "dependency failed",
    );
    // restart execution with new plan (or break, depending on strategy)
    continue;
  }

  // Inject results from dependencies into the tool input
  const enrichedInput = substituteDependencyResults(
    planStep.toolInput,
    results,
  );

  try {
    const output = await actionActivity(planStep.toolName, enrichedInput);
    results.push({ stepNumber: planStep.stepNumber, output, success: true });
  } catch (error) {
    results.push({
      stepNumber: planStep.stepNumber,
      output: error,
      success: false,
    });

    // Optional: replan on failure instead of just continuing
    plan = await replanActivity(context, plan, results, planStep, error);
  }
}

const answer = await respondActivity(context, plan, results);
```

The LLM gets called fewer times. In ReAct, the LLM runs on every loop iteration (thought, observation, thought, observation...). In Plan-and-Execute, the LLM runs once to plan, then execution is just tool calls, then the LLM runs once more to synthesize. For a 5-step task, ReAct might make 10+ LLM calls while Plan-and-Execute makes 2-3.

Trade offs exist between these architectures. ReAct is more flexible and can adapt to new information on the fly while Plan-and-Execute can be more efficient and better for tasks that require a lot of upfront planning. The best choice depends on the specific use-case and requirements of the agent being built.

#### Model Provider Reasoning Effort

Many Model Providers such as OpenAI, Anthropic, and Bedrock have arguments in their API that allow you to specify how much reasoning the model should do before returning a response. This can affect how the model decides when to call tools, when to ask clarifying questions, and how it generates its final response.

In some cases, you may want the model to do more reasoning and planning before taking any actions, which can lead to more accurate and useful responses. In other cases, you may want the model to take actions more quickly.

This can be used in combination with the 'thought' step of the ReAct agent architecture to improve the agents performance on complex tasks, by allowing it to do more reasoning before taking actions, and then using the outputs of those actions to inform its next steps.

For other steps, such as 'observation' or context 'compact' steps, we may want to have less reasoning, simply because it is not necessary, and would just add latency to the agent's response time and API costs.

#### Baysian Classifiers

Depending on the specific Agent use-case, sometimes the best answer is to remove some of the decision making from the LLM entirely, and instead use more traditional programming techniques to make decisions.

For example, if we have a specific set of tools that the agent can call, and we want to determine which tool to call based on the user's query, we could use a Bayesian Classifier to classify the user's query into one of several categories, and then map those categories to specific tools. This can be more efficient and cost effective than having the model determine which tool to call, especially if the categories are well defined and the mapping to tools is straightforward.
