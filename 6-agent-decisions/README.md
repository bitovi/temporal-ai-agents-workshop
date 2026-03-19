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

#### Challenges in Long Context Reasoning

One important topic to touch on here is how long context lengths impact the reasoning capabilities of large language models. This requires taking a look at how Transformer models, the uderlying architecture behind nearly every modern LLM, actually processes information. Specifically, we need to understand the self-attention mechanism and how it scales with context length.

_Attention is All You Need_

One of the major breakthroughs in Transformer models is the idea of Attention, described in the paper "Attention is All You Need" by researchers at Google in 2017.

At the core of every Transformer is the self-attention mechanism. When a model processes a sequence of tokens, each token computes an "attention score" against every other token in the sequence. These scores determine how much influence each token has on the representation of every other token. In simplified terms: attention is how the model decides what to pay attention to.

The attention scores are computed via a softmax function across all tokens in the context window. This means attention is inherently a competitive resource — the scores must sum to 1 across the full sequence. As the number of tokens grows, the attention budget gets spread thinner. A critical piece of information buried in token 50,000 of a 200,000-token context is competing for attention weight against 199,999 other tokens.

This mechanism works remarkably well for typical prompt lengths. But as context grows into the tens or hundreds of thousands of tokens, several practical problems emerge.

_Context Rot_

"Context rot" is a term coined by Anthropic to describe a phenomenon where model quality degrades as context length increases. As the number of tokens in the context window grows, the model's ability to accurately recall and reason over that context decreases. But the reality is more nuanced than just "more tokens = worse performance."

What we'd expect: If context rot were purely about attention dilution, models should struggle with basic retrieval tasks in long contexts — finding a specific fact ("needle") hidden in a large body of irrelevant text ("haystack"). But frontier models actually score 90%+ on needle-in-a-haystack benchmarks like RULER, even at very long context lengths. The models can find information in large contexts.

What actually happens: The degradation shows up on tasks that require reasoning over large contexts, not just retrieving from them. Tasks like aggregating information across thousands of entries, tracking state changes over long sequences, or synthesizing insights from distributed evidence across a large document. The model can find any individual piece of information, but struggles to hold and manipulate many pieces simultaneously.

This suggests context rot is caused by a combination of factors, not just attention dilution:

- Attention score dilution: With more tokens competing for attention weight, the model's ability to maintain sharp focus on the most relevant information decreases. Critical relationships between distant tokens can get "washed out" in the noise.
- Lost in the middle: Research has shown that models attend more strongly to tokens near the beginning and end of their context window, with weaker attention to information in the middle. This "U-shaped" attention pattern means that where information appears in the context matters almost as much as whether it's there at all.
- Training data distribution: Models are trained predominantly on sequences much shorter than their maximum context window. Ultra-long sequences are statistically rare in training data, making them effectively out-of-distribution at inference time. The model has less practice reasoning over very long inputs.
- Positional encoding limitations: Transformers use positional encodings to understand token ordering. Techniques like RoPE (Rotary Position Embeddings) and ALiBi have extended positional awareness, but extrapolating to positions far beyond training lengths still introduces degradation.
- MoE routing bottlenecks: For Mixture-of-Experts models (used by many frontier LLMs), the routing layer that selects which expert processes each token can become a bottleneck at extreme context lengths. The RLM authors noted this was a bigger factor than attention itself in some cases.

_Why This Matters for Agent Architecture_

Context rot is not just an academic concern. It has direct practical implications for how we build agents:

- ReAct loops accumulate context. In the ReAct architecture described earlier, the model sees the entire conversation history — every thought, action, and observation — on each iteration. After 10+ iterations of tool calls and observations, the context can grow substantially, and the model's reasoning quality in later iterations may degrade compared to earlier ones. This is one reason why a ReAct agent might "forget" its original goal or start making worse decisions in later iterations of a long-running task.
- Plan and Execute mitigates this by design. The Plan and Execute architecture naturally reduces the context rot problem. The planning step gets the full context and reasoning power, but each execution step operates with a minimal, focused prompt for a single task. The executor doesn't need to hold the entire problem history — just the specific step it's executing. This is one of its key architectural advantages over ReAct for complex, multi-step tasks.
- Reasoning effort settings interact with context length. When we use higher reasoning effort (as discussed in the Model Provider Reasoning Effort section), the model generates more internal reasoning tokens. These tokens also consume context window space and attention budget. For very long contexts, there's a tension between wanting deep reasoning and the additional context pressure that reasoning tokens create.

This is a fundamental motivation for building multi-agent systems, which we will talk a lot more about in the later sections.

#### Recursive Language Models: Treating Context as an Environment

One of the most interesting recent developments in how LLMs handle reasoning and complexity over long contexts is the concept of Recursive Language Models (RLMs). The idea is to treat the context as an external environment and allow the LLM to programmatically examine, decompose, and recursively call itself over snippets of the context. This approach enables the model to process inputs much longer than its native context window and can significantly improve performance on complex tasks.

Rather than feeding a long prompt directly into a model's context window, the input is stored as a variable in a Python REPL environment. With this approach the model can write code to programmatically inspect, transform, and recursively perform sub-queries over that data. The model can treat the prompt as something to interact with rather than something to consume all at once.

From the outside, an RLM call looks identical to a normal LLM API call. We pass in a query and context, and you get back a string response. But under the hood, the model is orchestrating its own recursive decomposition of the problem.

The RLM stores the, potentially enormous, context as a Python variable in a REPL. The model receives only the query and a reference to that variable. It can then:

- Peek at subsets of the data (e.g., print(context[:2000]))
- Search using regex, keyword matching, or Python string operations
- Transform the data with arbitrary Python code
- Recursively call itself (or a smaller/cheaper model) over slices of the data

When the Root model spawns a recursive query, that sub-Model gets its own fresh context window with only the subset of data it needs. The sub-Model's result is passed back to the root LM as a return value.

One of the most important and compelling aspects of RLMs is that the model can develop its own strategies for working with data. We don't need to define a fixed chunking strategy or retrieval pipeline. In fact, the Recursive Language Models (RLM) paper documents several patterns that emerge naturally:

- Peeking: The Root Model starts by inspecting the first few thousand characters of the context to understand its structure — exactly like a programmer opening a new dataset and running `head()`.
- Grepping: To narrow the search space, the model uses regex patterns or keyword matching over the context. This is far cheaper and faster than semantic retrieval, and the model decides when it's appropriate.
- Partitioning + Mapping: For tasks requiring semantic understanding across the full context, the model chunks the data and launches parallel recursive sub-calls over each chunk. For example, if asked to classify thousands of entries, the root LM might partition into groups of 100 and ask sub-calls to label each group, then aggregate.
- Summarization: The model naturally summarizes intermediate results from sub-calls, condensing information before making final decisions. It only summarizes when it determines it's the right strategy.
- Programmatic Solutions: For tasks that are fundamentally computational, the RLM can bypass the LLM entirely for that portion and just write Python code to compute the answer directly.

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
