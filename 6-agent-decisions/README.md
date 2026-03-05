# Exercise 6 - Agent Decisions

## Goals

The goal of this exercise is to understand how different agent decision strategies can be implemented and how they affect the behavior of an LLM-based Agent.

- **Reasoning and Acting (ReAct) Agent Architecture:** Structure agents to both reason about problems and take actions (e.g., calling tools, asking clarifying questions) to solve them.
- **Plan and Execute Agent Architecture:** Structure agents to generate a complete plan before executing it step by step.
- **Model Provider Reasoning Effort:** Explore how much reasoning the model provider (OpenAI, Anthropic, Bedrock, etc.) does before returning a response.
- **Techniques for Optimizing Decision Making:** Learn methods to improve agent consistency, reliability, and performance.

## What you need to know

### How it works

Take a look at a few different strategies for agent decision making, and how they affect the behavior of the agent.

#### Reasoning and Acting Agent Architecture

As we saw in Exercise 5, we can build an agent workflow that can run multiple iterations of a reasoning and acting loop, allowing the model to call tools, collect information, ask clarifying questions, and then generate a final response.

In this architecture the 'thought' step of our loop loop is where the model can reason about the problem, plan steps to solve it, and determine what actions are needed to work towards a solution. The other steps of the loop are more focused on executing those actions and collecting information, without needing as much reasoning effort from the model. In fact, this can be a useful way to optimize for cost and speed, by using the most powerful reasoning capabilities and largest models only in the 'thought' step, and then using smaller models with little to no reasoning effort in the other steps.

With Temporal Workflows, Activities, and Signals we can build a flexible agent architecture that can handle complex interactions, maintain state across potentially infinite iterations.

```ts
interface ThoughtResult {
  reasoning: string;
  action?: Action;
  answer?: string;
}

interface Action {
  name: string;
  input: Record<string, any>;
}
```

The Activities in our ReAct Agent Workflow look like this:

```ts
interface Activities {
  // Takes the context of the conversation, available tools, thinks about the first action
  thoughtActivity(context: string[], availableTools: Tool[]): ThoughtResult;

  // Takes the tool and input, executes the action
  actionActivity(toolName: string, input: ActionInput): string;

  // Takes the observation from the action and updates the context
  observationActivity(context: string[], result: string): string;
}
```

#### Plan and Execute Agent Architecture

Another common agent architecture is the 'plan and execute' architecture.
The core loop is: Plan, Execute (step-by-step), Evaluate, and then optionally loop/re-Plan as needed.
This is a similar approach to ReAct, however in this version the Planning step attempts to create the entire list of tasks that will be required to solve the problem upfront.

Plan and Execute is an architecture where we separate the 'thinking' steps from the 'doing' steps.
The LLM acts as a sort of compiler — looking at the complex question, breaking it down into steps of tool calls, and then letting the executor take it from there.
It is up to the Plan step to perform the strategic thinking, and it is up to the Execute step to think only about each individual task.
This means we can use our most powerful reasoning model for the planning step, while the tool calls and result parsing in the execution steps can be handled by a fast, cheap model.

In ReAct, the LLM sees the whole context of the entire problem, including all the previous steps, each time it decides what to do next.
With Plan-and-Execute, we can often reduce the amount of LLM context needed because each execution step is narrowly focused on its own task rather than the full problem.
If steps do not have dependencies on each other, they can even be executed in parallel.

The core difference is who decides what to do next. In your ReAct loop, the `thoughtActivity` decides the next action on every iteration with a one-step-at-a-time approach.
In Plan-and-Execute, a `planActivity` generates the full sequence of steps upfront, and then the workflow iterates through them, executing each one with a narrow focus.

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

  // The same as our existing `action` activity we use in ReAct Agent
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

The Workflow itself is where the structural difference really shows. In our existing ReAct Workflow, the loop is driven by the LLMs decisions each iteration, with each iteration getting the entire result of the previous steps.

In Plan-and-Execute, the LLM runs once to plan, then execution is just tool calls, maybe with a very light LLM call to format output, then the LLM runs once more at the end to generate a final result.
For a 5-step task, ReAct might make 10+ LLM calls while Plan-and-Execute might only make 2-3, and with a much smaller number of tokens used.

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

Plan-and-Execute aims to use LLMs more efficiently. 
The planning step gets the full reasoning power of a large model, but each execution step operates with a minimal prompt focused on a single task — making those calls faster and cheaper.
The plan also helps prevent drift, keeping the agent on track toward the original goal rather than getting sidetracked by intermediate results.

Trade-offs exist between these architectures. ReAct is more flexible and can adapt to new information on the fly, while Plan-and-Execute can be more efficient and better for tasks that benefit from upfront decomposition. The best choice depends on the specific use-case and requirements of the agent being built.

#### Model Provider Reasoning Effort

Many Model Providers such as OpenAI, Anthropic, and Bedrock have arguments in their API that allow you to specify how much reasoning the model should do before returning a response. This can affect how the model decides when to call tools, when to ask clarifying questions, and how it generates its final response.

In some cases, you may want the model to do more reasoning and planning before taking any actions, which can lead to more accurate and useful responses. In other cases, you may want the model to take actions more quickly.

This can be used in combination with the 'thought' step of the ReAct agent architecture to improve the agents performance on complex tasks, by allowing it to do more reasoning before taking actions, and then using the outputs of those actions to inform its next steps.

For other steps, such as 'observation' or context 'compact' steps, we may want to have less reasoning, simply because it is not necessary, and would just add latency to the agent's response time and API costs.

AWS Bedrock:

```java
Document reasoningConfig = Document.mapBuilder()
        .putDocument("reasoningConfig", Document.mapBuilder()
                .putString("type", "enabled")
                .putString("maxReasoningEffort", "low")
                .build())
        .build();
requestBuilder.additionalModelRequestFields(reasoningConfig);
```

OpenAI:

```java
ChatCompletionRequest request = new ChatCompletionRequest.Builder()
        .model("gpt-5.1") // Must be a reasoning model
        .messages(List.of(new ChatCompletionResponseMessage.Builder()
                .role("user")
                .content("Explain the theory of relativity in simple terms.")
                .build()))
        // Set the reasoning effort parameter
        .reasoningEffort(ReasoningEffort.HIGH) // Or LOW, MEDIUM, XHIGH, etc.
        .build();
```

#### Techniques for Optimizing Decision Making

##### Baysian Classifiers

Depending on the specific Agent use-case, sometimes the best answer is to remove some of the decision making from the LLM entirely, and instead use more traditional programming techniques to make decisions.

For example, if we have a specific set of tools that the agent can call, and we want to determine which tool to call based on the user's query, we could use a Bayesian Classifier to classify the user's query into one of several categories, and then map those categories to specific tools. This can be more efficient and cost effective than having the model determine which tool to call, especially if the categories are well defined and the mapping to tools is straightforward.

##### Rule-Based Decisions

Sometimes it can be useful to direct an agent’s decision-making process by defining explicit rules.

For example, a large bank building a financial AI agent might rely on rule-based decision making to ensure regulatory compliance and auditability. Deterministic rules can also serve as “guardrails” to protect against hallucinations or erratic behaviors in complex environments. For instance:
`if (applicant.creditScore < 400) { requestManualReview(); }`

A sophisticated AI agent may take a hybrid approach using rules for high-frequency, structured tasks (like refund approvals) while leveraging LLM inference for unstructured, adaptive problem-solving.

##### Optimization Algorithms

LLMs typically generate responses by predicting the most likely next token at each step — a process known as probabilistic generation. To achieve better reasoning and results, we often need more strategic selection techniques that look beyond immediate next steps and optimize for the overall outcome.

**Best-of-N:** Run the prompt multiple times (e.g., 10), then use a separate 'Reward Model' or 'Validator' to score and select the best response. This brute-force approach is highly effective for coding or math tasks.

**Monte Carlo Tree Search (MCTS):** For complex, multi-step tasks, MCTS allows the agent to 'look ahead' at the consequences of actions before committing, similar to how AlphaGo evaluates chess moves.

**Beam Search:** By keeping the top 3 or 5 paths open simultaneously, beam search helps avoid the 'Greedy Algorithm' trap, optimizing for the final outcome rather than just the immediate next step.

##### Reinforcement Learning

Reinforcement Learning (RL) enables agents to improve by learning from experience and remembering past outcomes.

When an agent makes a mistake—such as hallucinating a tool's capability or failing a multi-step task—a negative reward signal forces it to adjust its internal reasoning policy. Over time, this turns every failure into a training data point.

In enterprise settings, RL allows your AI to become a 'digital worker' that learns specific edge cases and refines its logic, rather than remaining at its initial performance level.

##### Multi-Agent Coordination

Single agents often hit reasoning walls, such as hallucination loops or difficulties with complex, multi-step planning. To overcome this, we treat AI as a digital team.

**Collaborative modes:** Specialized agents (e.g., researcher, writer, critic) debate and verify each other’s work, ensuring higher accuracy.

**Competitive modes:** Agents are pitted against each other to find flaws or edge cases, using game-theory dynamics to reach the most resilient decision possible.

By balancing cooperative and competitive forces, we move from simple automation to proactive, collective intelligence.
