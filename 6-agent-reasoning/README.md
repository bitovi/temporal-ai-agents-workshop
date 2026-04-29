# Exercise 6 - Agent Reasoning

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

ReAct (Reasoning and Acting) is a way to enable LLMs to combine Chain of Thought and multi-step reasoning with tool use, allowing the model to iterate on a problem until it is solved. Rather than following a predefined workflow, ReAct relies on the LLM's reasoning capabilities to dynamically adjust its approach based on new information gathered from previous steps.

This enables the LLM to think aloud, plan the next steps, use tools to fetch information or interact with external systems, and then observe the resulting state (context) that it has collected. This cycle of **Thought → Action → Observation** is the reasoning and acting loop.

As we saw in Exercise 5, we can build an agent workflow that can run multiple iterations of a reasoning and acting loop, allowing the model to call tools, collect information, ask clarifying questions, and then generate a final response.

In this architecture the 'thought' step of our loop loop is where the model can reason about the problem, plan steps to solve it, and determine what actions are needed to work towards a solution. The other steps of the loop are more focused on executing those actions and collecting information, without needing as much reasoning effort from the model. In fact, this can be a useful way to optimize for cost and speed, by using the most powerful reasoning capabilities and largest models only in the 'thought' step, and then using smaller models with little to no reasoning effort in the other steps.

Each step in the loop has a specific role:

- **Thought:** This is where we give the agent a place to literally think about what it needs to do next, based on the current state of the context and the original question. Looking at the available tools and the information already gathered, the Thought step returns either an **Action** to take next, or a final **Answer** if the agent has enough information to respond.
- **Action:** If the Thought step returned an Action, we execute it. An Action is a call to one of our defined tools with the inputs the model decided on (returned as a structured output from the LLM). The raw result of that tool call is then passed along to the Observation step.
- **Observation:** Tool results are often noisy or larger than we need. The Observation step uses an LLM to extract or summarize the important information from the tool's output and incorporates it back into the context. That updated context then feeds into the next Thought, closing the loop until the agent produces a final Answer.

The Workflow runs through this Thought → Action → Observation loop as many times as needed, with every thought, action, and observation accumulating into the LLM's context. Because the LLM is involved at each step — generating reasoning in Thought, producing the structured tool call in Action, and summarizing results in Observation — the agent stays adaptive across iterations. This makes ReAct very flexible, easy to implement, and easy for humans to reason about.

One thing to keep in mind here is that we can, optionally, perform some optimizations on which LLM we use at different parts of the loop. For the Thought step we want a model that excels at complex decision making and long-term planning — usually the biggest, most state-of-the-art model we can use. For other steps like Observation (or in our example, score rubric adherence), we can use a much smaller, faster model whose only job is to take the text in its context window and summarize it or extract facts from a provided document.

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

The single most important output of the Planning step is the **dependency map** between steps — which steps need results from which earlier steps. This map is what makes the rest of the workflow possible: it lets the executor know what order steps must run in, and just as importantly, which steps can run in **parallel**. Just like ReAct's Actions, the steps in the plan are typically tool calls used to gather data, perform calculations, and interact with external systems.

If we take a look at this in diagram form, we can see the two major sides of the flow:

![Plan and Execute](../.images/plan-and-execute.png)

Plan on the left and Execute on the right. The link between them, initially, is when the Plan hands off the list of tasks that need to be executed. The execution phase begins, processing all of the steps, until they are all complete. Once they are, a final LLM can generate a response. Depending on the specific use case we’re going after, we can often implement the entire Execute phase without an LLM involved at all. If we do end up needing an LLM in some places, at least the input remains very small as it only needs to be aware of its own tasks and any tasks it depends on. The complexity here is around creating that list of tasks and tracking their dependencies to each other. The creation of this plan and dependency chain is going to rely on the model giving us great structured output in some format like JSON

The data structure we are actually trying to get the Planner to produce is a **Directed Acyclic Graph (DAG)**.

In a naive Plan and Execute agent, the plan could just be a flat ordered list: do step 1, then step 2, then step 3. But many real plans have steps that are completely independent of each other and could run in parallel, while other steps have genuine dependencies on the outputs of earlier steps. A DAG captures this naturally:

- **Nodes** = individual tasks or sub-goals.
- **Edges** = dependencies, meaning "this task requires the output of that task."
- **Acyclic** = there are no cycles in the graph, so the plan always terminates and progresses forward.

Because of these properties, we can implement a straightforward algorithm to walk the graph: each step has a tool name, a defined set of inputs, and a list of `stepId`s it depends on. As dependencies resolve, their outputs are substituted into the dependent step's inputs. Once all of a step's dependencies are met, that step is ready to execute. Independent branches of the DAG can run in parallel, while dependent branches must wait for their inputs.

Let's take a look at what this looks like implemented in code.

See [Plan and Execute code example](../.carbon/plan-and-execute-code.md).

The workflow starts the same way as our ReAct loop: we create storage for the context and seed it with the user's initial question. (For simplicity, this version does not handle Re-Planning so it can fit on a single slide.) The first real step is the **Plan Activity**, which receives the context — at this point just the user's question — and runs our strict prompt that explains how to build the plan, what JSON format we expect, and what tools are available. Once the plan comes back, we initialize a `PlanStatus` to track each step's state: which have results, which have failed. Initially, none. The execution loop then filters for steps whose dependencies are all met and adds them to a pending list — when that pending list is empty, we know all the work is done. Temporal lets us run Activities in parallel, so we kick off `executePlanStep` for each ready step concurrently, await all of them, and merge the results back into the plan and context. If any step fails, it goes into the `PlanStatus` failed list. Once execution finishes, we hand the full plan, results, and context to the `executeResponse` Activity, which uses the LLM one final time to format everything into the user-facing response. If there were failures, we can either trigger a fresh Plan with the gathered information or, after some max number of iterations, give up.

Example Plan and Execute:

See [Plan and Execute code example] (../.carbon/plan-and-execute-example.md)

As an example, take the question: _"What is the number of daily League of Legends players, and what is that number times the distance from the Earth to the Sun?"_ As humans, we can immediately see this is really three tasks: look up the player count, look up the distance, and multiply them together. The hard reasoning is in the decomposition itself — once it's broken down, the individual steps are simple, and all we need is something to dispatch each task and order them by their dependencies. Each step in the plan must declare what it depends on, and may also need to describe the shape or type of its output so later steps know how to consume it. That structured JSON is the output of the Planning phase, generated by the LLM. Once it's produced, we hand it off to the Execute phase, which works through the steps in dependency order until the plan is complete.

In this example, steps 1 and 2 have no dependencies, so they can be executed in parallel and their numerical results collected concurrently. Step 3 depends on the results of both steps 1 and 2, so it can only run once those have completed — and you can see in its definition that it uses **placeholders** referencing the outputs of steps 1 and 2 instead of hardcoded values. Depending on the complexity of the workflow and the tools involved, the Executor might not need an LLM at all: we could write fully deterministic Workflow code that runs the tasks in dependency order, collects their outputs, and does simple string replacement to inject those outputs into the inputs of later steps. In practice, we may still want a lightweight LLM call inside the Executor to coerce a tool's output into the exact shape the next step needs, but those calls remain much smaller and faster than the full Observation step of ReAct.

The last thing to think about is **error handling**. If step 2 failed, or returned a string instead of a number, step 3 can no longer execute because its dependencies haven't been met. At that point we can call the original Plan step again, passing in which steps completed successfully, which failed, and the relevant outputs, and ask for a fresh plan that takes the current state into account.

Getting the LLM to reliably produce a DAG requires a very specific and strict prompt. We need to use **Structured Output** here — providing the model with a JSON Schema (or similar structure) and validating that its response matches that format. The prompt needs to clearly tell the model:

- Here is the user's goal.
- Here are the tools available to you, including their inputs and outputs.
- Break the goal down into a sequence of steps.
- Number each step and track them individually.
- For inputs that come from a previous step, use a placeholder referencing that step's output rather than guessing a value.
- Maintain the dependency chain between steps.

Equally important is how we describe our **tools** to the Planner. The tool definitions need to make it clear what each tool's inputs and outputs are, so the Planner knows what it will (and won't) get back from a given tool. For example, if a user asks for the weather in Rochester, NY and we have a Weather tool defined, that sounds great — but if the user specifically asks about the current wind speed and our Weather tool doesn't actually expose wind speed, it's hard for the model to plan around that without a clear definition of the tool's outputs.

Take an earlier example: comparing the number of League of Legends players to the distance to the sun. We want the Planner to recognize that in order to multiply two numbers together, it needs to first figure out what those two numbers actually _are_. The plan should include separate steps to look up each value, marked as dependencies of the multiplication step. Making good use of the tools, understanding their outputs, and keeping track of what depends on what is the key to making Plan and Execute work successfully.

The last part to talk about here is the potential for Re-Planning. If something goes wrong during our task execution, we need to recognize this and report back to the Planner that we’re unable to continue and we need a new plan.

In an ideal implementation, the only step that requires an LLM is the initial Planning phase. The remainder of the workflow can often be implemented as completely normal deterministic Workflow code, which dramatically reduces LLM calls, lowers cost, and improves overall latency.

In ReAct, the LLM sees the whole context of the entire problem, including all the previous steps, each time it decides what to do next.
With Plan-and-Execute, we can often reduce the amount of LLM context needed because each execution step is narrowly focused on its own task rather than the full problem.
If steps do not have dependencies on each other, they can even be executed in parallel.

The core difference is who decides what to do next. In your ReAct loop, the `thoughtActivity` decides the next action on every iteration with a one-step-at-a-time approach. This makes ReAct very flexible and well suited for robust agents that can adapt on the fly.
In Plan-and-Execute, a `planActivity` generates the full sequence of steps upfront, and then the workflow iterates through them, executing each one with a narrow focus. This is less dynamic — if something goes wrong during execution, the workflow has to restart by creating a new plan, feeding back in the information that was gathered so far.

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

Everything we've talked about so far has been about building a system _around_ the LLM that lets us construct and manage the "thought" or "plan" the model uses to solve a problem. But there are other layers of reasoning we can take advantage of — what if the model itself could think internally and reason before answering?

Older LLMs (just a couple of years ago) generated tokens in a single pass — basically thinking as they wrote, relying entirely on their internal representation of the world and whatever context they were given to predict the next token. Agent loops and external Chain of Thought scaffolding turned out to be so effective at reducing errors and improving output that LLM providers started training models to generate "reasoning tokens" before producing any visible text for the user.

These reasoning tokens aren't anything magical. They simply give the model an internal notepad — a space to emit tokens that aren't shown to the user, where it can explore different approaches, plan, and self-correct before committing to an answer. Think of it as the difference between blurting out the first thing that comes to mind versus pausing to work through the problem on paper before speaking. The model is still just generating text; the tokens are just structured the way a person might reason about the problem instead of jumping straight to an answer and hoping it's right. The benefit is that it makes it much more likely that relevant information from the context and training data will surface during the thinking process and be incorporated into the final response.

Most modern models can take advantage of this — OpenAI, Anthropic, Gemini, and even smaller locally-run models like Qwen and DeepSeek all support some form of internal reasoning. This process is commonly referred to as **Chain of Thought (CoT)**. Instead of jumping straight to an answer, the model generates a sequence of intermediate "thinking" tokens that work through the problem step by step. This often produces more accurate results on tasks that involve math, logic, planning, or multi-step decision making.

Many Model Providers such as OpenAI, Anthropic, and Bedrock expose arguments in their API that allow you to specify how much Chain of Thought reasoning the model should do before returning a response. This can affect how the model decides when to call tools, when to ask clarifying questions, and how it generates its final response.

In some cases, you may want the model to do more reasoning and planning before taking any actions, which can lead to more accurate and useful responses. In other cases, you may want the model to take actions more quickly with little to no Chain of Thought.

This can be used in combination with the 'thought' step of the ReAct agent architecture to improve the agents performance on complex tasks, by allowing it to do more reasoning before taking actions, and then using the outputs of those actions to inform its next steps. In effect, this layers two forms of reasoning together: the model's internal Chain of Thought inside each call, and the explicit reasoning step the workflow itself enforces between calls.

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

##### Why might we NOT want this?

Internal reasoning isn't free. Every model still has a **fixed context length**, and while those windows are getting larger, we still pay per token for both input and output. Output tokens are typically billed at a noticeably higher rate than input tokens, and **reasoning tokens count against our output token usage** — even though we never actually see them. We also see **latency increase**, because the model now has to generate all those reasoning tokens before it begins emitting the visible response.

Thankfully most Model Providers let us tune this behavior, so we can dial reasoning effort up or down based on the use case:

- **Low effort:** if a request is just summarizing some provided text, we likely don't need the highest level of reasoning.
- **High effort:** if a request is the Planning phase of a Plan and Execute agent, where the model has to follow strict instructions and produce very specific structured output, we probably want to crank reasoning effort up.

Let's look at some specific examples of these reasoning tokens to see what sorts of things they might contain.

See [Reasoning tokens example answer](../.carbon/reasoning-tokens-example-answer.md).

As an example, I asked a reasoning model running locally on my laptop the prompt _"Can you tell me about LLM reasoning tokens?"_ The screenshot in that linked file shows the actual visible answer — about 11 lines, totaling 238 output tokens, and a pretty solid response. The model is from the **Qwen** family (recently released by Alibaba Cloud), and because it's running locally I can also see every reasoning token it generated. Before producing those 238 visible tokens, the model emitted **62 additional lines** — close to **1,000 reasoning tokens** — of internal thought.

What's interesting is what's actually in those reasoning tokens. The model sets a goal, breaks the request down, holds the system prompt's persona in mind, and considers multiple interpretations of the phrase "LLM reasoning tokens." It then starts drafting the actual response — planning what each paragraph should contain, working through the bullet points, drafting most of the answer, and even leaving itself little reminders about final polish (like remembering to end each paragraph with two newlines).

#### The Future of Model Reasoning

This area is a very active research space right now. I won't go too deep into it, but it's worth flagging that some of the most interesting papers in the last six months are exploring how reasoning could be introduced to models **without relying on natural-language output at all**.

The theory is that reasoning in natural language — while much better than no reasoning at all — may itself be a bottleneck. A meaningful portion of the token budget for current reasoning tokens goes toward maintaining full, fluent linguistic output rather than actually advancing the reasoning. So when we allow X tokens for reasoning, a chunk of that budget (and a lot of compute) is spent on language fluff. If models could "reason" directly in their own latent embedding space, they might do even better.

A few notable papers in this space:

- **"Training Large Language Models to Reason in a Continuous Latent Space" (COCONUT)** — Hao et al., Nov 2025 (Meta).
  COCONUT pushes beyond text-based reasoning tokens entirely. Instead of expressing reasoning through language tokens, it uses the LLM's last hidden state as a "continuous thought" representation, feeding it back into the model as the next input embedding directly in continuous space. The argument is that natural language is a bottleneck for reasoning — much of the token budget in standard CoT goes to maintaining linguistic coherence rather than advancing the reasoning. A nice side effect: COCONUT can encode multiple alternative next steps simultaneously, enabling breadth-first exploration of the reasoning space.

- **"Reasoning Beyond Language: A Comprehensive Survey on Latent Chain-of-Thought Reasoning"** — May 2025.
  A survey that categorizes latent reasoning methods into **intrinsic** approaches (keeping the entire pipeline inside a single LLM) and **auxiliary** approaches (introducing a separate module that generates continuous tokens injected into the main model). It covers COCONUT, HCoT, CCoT, SoftCoT, CoCoMix, and others — useful if you want a map of where the field is heading.

- **"Demystifying Long Chain-of-Thought Reasoning in LLMs"** — February 2025.
  Focuses on the transition from short CoT to long CoT reasoning and the training dynamics involved, particularly how SFT (supervised fine-tuning) and RL (reinforcement learning) contribute to extended reasoning chains.

#### Instruction Tuning

As we talked through reasoning tokens and model output, you might have been wondering: how do they actually get the model to output those reasoning tokens first in the first place? The answer is a process called **fine-tuning** — and more specifically, **instruction fine-tuning** (or "instruction tuning").

Fine-tuning is a general machine learning principle: the process of adapting a model for some specific task or use case. When we talk about large language models, we tend to describe them as very clever next-word prediction machines, and in their most raw form — as **foundation models** — that's exactly what they are. Foundation models are the giant, computationally expensive models trained on enormous datasets containing roughly all of human-readable text. But after that initial pretraining, all the model really knows how to do, from its parameters and weights alone, is predict what the next token should be.

**Instruction tuning** is the process Google, OpenAI, Amazon, Anthropic, and others use to improve model performance not just on specific tasks like coding, but on **following instructions in general** — adapting the model for practical use. Instead of training on raw text, instruction tuning uses curated datasets of input/output pairs. After thousands (or, for frontier models, millions) of examples, the model learns to understand what a user is actually asking for, when a tool call is required, and how to format its responses appropriately.

This additional fine-tuning step is also where reasoning-token behavior gets baked into a model. Models are shown many examples of "showing their work" — output that resembles how a person might reason through a problem — so the model learns to produce that kind of intermediate output before its final answer.

One of my favorite illustrations of the difference comes from [Dave Bergmann at IBM](https://www.ibm.com/think/topics/instruction-tuning). Given the input _"teach me how to bake bread,"_ a base foundation model might just continue the sentence with something like _"in a home oven."_ An instruction-tuned model, by contrast, recognizes — based on the question/answer pairs it was tuned with — that the user wants an actual recipe and step-by-step instructions, and responds accordingly.

Let's take a look at how we can do this ourselves right on AWS Bedrock.

#### AWS Bedrock Fine-tuning

As we discussed, fine-tuning lets us (hopefully) improve a model's performance on specific tasks by providing a labeled dataset. During this process, the model learns the relationship between inputs and desired outputs and adjusts its parameters accordingly. This makes it particularly useful for tasks where domain-specific knowledge is essential — by giving the model examples of your specific data, you can enhance its ability to produce accurate and relevant results for your application.

AWS Bedrock lets us create model fine-tuning jobs to customize the behavior of supported models. These jobs can be kicked off either through the AWS Console or programmatically with the `boto3` Python library. For reinforcement fine-tuning, you can provide up to **20,000 examples** per job. See the [Bedrock RFT documentation](https://docs.aws.amazon.com/bedrock/latest/userguide/rft-nova-models.html) for full details.

Each example in the dataset uses two key fields:

- **`messages`** — the user, system, or assistant role containing the input prompt provided to the model.
- **`reference_answer`** — the expected output or evaluation criteria your reward function uses to score the model's response. This isn't limited to structured output; it can be any format that helps your reward function evaluate quality.

#### Long Context Reasoning

One important topic to touch on here is how long context lengths impact the reasoning capabilities of large language models. This requires taking a look at how Transformer models, the uderlying architecture behind nearly every modern LLM, actually processes information. Specifically, we need to understand the self-attention mechanism and how it scales with context length.

_Attention is All You Need_

One of the major breakthroughs in Transformer models is the idea of Attention, described in the paper "Attention is All You Need" by researchers at Google in 2017.

At the core of every Transformer is the self-attention mechanism. When a model processes a sequence of tokens, each token computes an "attention score" against every other token in the sequence. These scores determine how much influence each token has on the representation of every other token. In simplified terms: attention is how the model decides what to pay attention to.

The attention scores are computed via a softmax function across all tokens in the context window. This means attention is inherently a competitive resource — the scores must sum to 1 across the full sequence. As the number of tokens grows, the attention budget gets spread thinner. A critical piece of information buried in token 50,000 of a 200,000-token context is competing for attention weight against 199,999 other tokens.

**A quick note on softmax:** softmax is the process of taking a bunch of raw numbers — in this case, the relevance scores from all the tokens in the context — and converting them into a **probability distribution**: a set of values between 0 and 1 that all add up to 1. The key thing about softmax is that it's competitive. If the score of one item goes up, the others must go down proportionally because the total is fixed at 1. In the context of attention, the model computes a raw relevance score for every token pair, and softmax converts those into attention weights. So when you have 200K tokens, every token's attention weight is competing against 199,999 others within a fixed budget of 1.0 — which is exactly why attention dilution becomes a real concern at long context lengths.

This mechanism works remarkably well for typical prompt lengths. But as context grows into the tens or hundreds of thousands of tokens, several practical problems emerge.

_Context Rot_

"Context rot" is a term coined by Anthropic to describe a phenomenon where model quality degrades as context length increases. As the number of tokens in the context window grows, the model's ability to accurately recall and reason over that context decreases. But the reality is more nuanced than just "more tokens = worse performance."

What we'd expect: If context rot were purely about attention dilution, models should struggle with basic retrieval tasks in long contexts — finding a specific fact ("needle") hidden in a large body of irrelevant text ("haystack"). But frontier models actually score 90%+ on needle-in-a-haystack benchmarks like RULER, even at very long context lengths. The models can find information in large contexts.

What actually happens: The degradation shows up on tasks that require reasoning over large contexts, not just retrieving from them. Tasks like aggregating information across thousands of entries, tracking state changes over long sequences, or synthesizing insights from distributed evidence across a large document. The model can find any individual piece of information, but struggles to hold and manipulate many pieces simultaneously.

This suggests context rot is caused by a combination of factors, not just attention dilution:

- Attention score dilution: With more tokens competing for attention weight, the model's ability to maintain sharp focus on the most relevant information decreases. Critical relationships between distant tokens can get "washed out" in the noise.
- Lost in the middle: Research has shown that models attend more strongly to tokens near the beginning and end of their context window, with weaker attention to information in the middle. This "U-shaped" attention pattern means that where information appears in the context matters almost as much as whether it's there at all — so **how we organize our context can have a real effect on the output we get**.
- Training data distribution: Models are trained predominantly on sequences much shorter than their maximum context window. Ultra-long sequences are statistically rare in training data, making them effectively out-of-distribution at inference time. Tying this back to instruction tuning — how many of those tuning samples are actually examples of reasoning over an entire encyclopedia worth of information? Very few. The model simply has less practice reasoning over very long inputs.
- Positional encoding limitations: Transformers use positional encodings to understand token ordering. Techniques like RoPE (Rotary Position Embeddings) and ALiBi (Attention with Linear Biases) have extended positional awareness, but extrapolating to positions far beyond training lengths still introduces degradation.
- MoE routing bottlenecks: For Mixture-of-Experts models (used by many frontier LLMs), the routing layer that selects which expert processes each token can become a bottleneck at extreme context lengths. The RLM authors noted this was a bigger factor than attention itself in some cases.

_Why This Matters for Agent Architecture_

Context rot is not just an academic concern. It has direct practical implications for how we build agents:

- ReAct loops accumulate context. In the ReAct architecture described earlier, the model sees the entire conversation history — every thought, action, and observation — on each iteration. Depending on the use case and how much information is being pulled in, after 10, or even 100, iterations of tool calls and observations the context can grow substantially, and the model's reasoning quality in later iterations may degrade compared to earlier ones. This is one reason why a ReAct agent might "forget" its original goal or start making worse decisions in later iterations of a long-running task.
- Plan and Execute mitigates this by design. The Plan and Execute architecture naturally reduces the context rot problem. The planning step gets the full context and reasoning power, but in the best case each execution step operates with **no LLM inference at all** — and if it does, it's with a minimal, focused prompt for a single task. The executor only needs to be aware of the specific step it's executing. The trade-off is that Plan and Execute is much harder to implement for open-ended use cases where the agent has to search the web or work with unstructured data — it's difficult to plan around data you haven't seen yet. But for more specialized agents with well-known tools and outputs, it can be extremely effective.
- Reasoning effort settings interact with context length. When we use higher reasoning effort (as discussed in the Model Provider Reasoning Effort section), the model generates more internal Chain of Thought tokens. These tokens also consume context window space and attention budget. For very long contexts, there's a tension between wanting deep reasoning and the additional context pressure that Chain of Thought tokens create.

This is a fundamental motivation for building multi-agent systems, which we will talk a lot more about in the later sections.

#### Recursive Language Models: Treating Context as an Environment

Recursive Language Models (RLMs) are a fairly new and very interesting approach for solving some of the long-context problems we just discussed. In a way, RLMs combine ideas from both ReAct and Plan and Execute: there's a **Root** language model — our top-tier reasoning model — that acts as an orchestrator, performing the initial thinking and planning, and then splitting the problem up into sub-queries assigned to sub-Agents that work over slices of the context. The Root keeps its own context focused only on what it needs to solve the problem.

The other defining feature of RLMs is that they lean hard into something modern frontier models are very good at: **writing simple Python**. Instead of feeding a massive context directly into the model's context window (which we often can't, because it's too big), the context is stored as a variable inside a Python **Read-Eval-Print Loop (REPL)** environment. The Root agent then uses tool calls to write Python code that runs against that environment. From the outside, an RLM call looks identical to a normal LLM API call — query in, string out — but under the hood the model is orchestrating its own recursive decomposition of the problem.

With the context held as a variable in the REPL, the agent can:

- Peek at subsets of the data (e.g., `print(context[:2000])`)
- Search using regex, keyword matching, or any Python string operations
- Transform the data with arbitrary Python code, extract pieces into new variables
- Recursively call itself (or a smaller/cheaper model) over slices of the data

When the Root model spawns a recursive query, that **Sub-Agent** is essentially the same as the Root Agent with one important restriction: Sub-Agents cannot spawn additional Sub-Agents. Each Sub-Agent gets its own fresh REPL initialized with whatever chunk of the original context the Root assigned to it, and its result is returned to the Root agent as a normal return value.

One of the most important and compelling aspects of RLMs is that the model can develop its own strategies for working with data. We don't need to define a fixed chunking strategy or retrieval pipeline. In fact, the Recursive Language Models (RLM) paper documents several patterns that emerge naturally:

- Peeking: The Root Model starts by inspecting the first few thousand characters of the context to understand its structure — exactly like a programmer opening a new dataset and running `head()`.
- Grepping: To narrow the search space, the model uses regex patterns or keyword matching over the context. This is far cheaper and faster than semantic retrieval, and the model decides when it's appropriate.
- Partitioning + Mapping: For tasks requiring semantic understanding across the full context, the model chunks the data and launches parallel recursive sub-calls over each chunk. For example, if asked to classify thousands of entries, the root LM might partition into groups of 100 and ask sub-calls to label each group, then aggregate.
- Summarization: The model naturally summarizes intermediate results from sub-calls, condensing information before making final decisions. It only summarizes when it determines it's the right strategy.
- Programmatic Solutions: For tasks that are fundamentally computational, the RLM can bypass the LLM entirely for that portion and just write Python code to compute the answer directly.

#### Techniques for Optimizing Decision Making

Now that we've covered a lot of agent- and model-centric reasoning techniques, it's worth asking: are there things we can do at the **system level**, before the LLM or agent ever gets involved, that can help the reasoning process? And given the non-deterministic nature of LLMs and agents, we may not always get the results we want — what can we do to mitigate that?

In our agent system (our Temporal Workflow) there are several places we can help steer the agent in the right direction:

- **Pre-processing steps**: when a user's message comes in, but before it gets sent to the agent.
- **Post-processing steps**: guardrails, validation, and escalation that run after the agent or LLM produces output.
- **Inline retrieval**: using RAG and other memory systems as part of the agent loop to fetch policy documents, business rules, or examples.

Let's look at some specific options.

##### Baysian Classifiers

Depending on the specific Agent use-case, sometimes the best answer is to remove some of the decision making from the LLM entirely, and instead use more traditional programming techniques to make decisions.

For example, if we have a specific set of tools that the agent can call, and we want to determine which tool to call based on the user's query, we could use a Bayesian Classifier to classify the user's query into one of several categories, and then map those categories to specific tools. This can be more efficient and cost effective than having the model determine which tool to call, especially if the categories are well defined and the mapping to tools is straightforward.

##### Rule-Based Decisions

Rule-based decision making is essentially **hard-coded business rules that run before or after the LLM generates a response, or before or after the agent loop executes a tool call**. They give us a deterministic safety net around the parts of the system where we don't want the LLM making the final call.

For example, if we have a customer-service AI agent that can process refunds, we might have a hard-coded business rule that checks tool inputs to ensure the agent **can't issue a refund greater than $100 without human approval**. We might also have analysis steps that check for internal company information leaking into outputs, or that screen incoming messages for potentially malicious users. We let the AI agent do all of its adaptive problem solving and natural-language interaction, but for important business-critical transactions we rely on this **second layer of validation**.

A simple example might look like:

`if (applicant.creditScore < 400) { requestManualReview(); }`

To make things feel seamless to both the user and the agent, we can also surface some of these rules in **natural language inside the agent's prompt** or via **RAG lookups** of policy documents, so the agent itself is aware of the constraints rather than just hitting a wall when it tries to do something disallowed.

A sophisticated AI agent often takes a hybrid approach — deterministic rules for high-frequency, structured tasks (like refund approvals) and LLM inference for unstructured, adaptive problem solving. In a regulated environment like a bank, rule-based decision making is also what gives us regulatory compliance and auditability, and serves as guardrails against hallucinations or erratic behaviors in complex environments.

**Strengths:**

- Predictable.
- Easy to audit.

**Weaknesses:**

- Requires more up-front effort to design and maintain.

##### Intent Classification

Before the agent ever sees a user's message, we can run a small classifier to figure out **what kind of request this actually is** — a refund inquiry, a billing question, an account update, a general FAQ, simple small talk, etc. Based on the classification we can then **tweak the agent's system prompt**, **pare down the list of available tools**, or **route to a completely different agent** purpose-built for that category of request.

This is more impactful than it might first sound. As we've discussed several times, agents almost always perform better when their context is focused and their prompt only contains information relevant to the problem at hand. Intent classification is one of the cleanest ways to enforce that focus from the very beginning of the workflow.

**Naive Bayes** is one of the standard approaches for this kind of text classification — it's the same family of algorithm used for spam filtering and sentiment analysis. It's a probabilistic machine learning algorithm based on Bayes' Theorem that classifies text by assuming all features (words, tokens) are independent of one another — the "naive" part of the name — regardless of their actual correlation. That assumption is mathematically wrong, but in practice the algorithm is fast, efficient, and surprisingly accurate for short-text classification. It can also be implemented with a small fine-tuned model or even a single LLM call with a tightly constrained Structured Output schema returning one of a fixed set of categories.

A concrete example: in one of my own agent projects, intent classification did a great job of cutting down costs for simple small-talk messages. If a user just writes _"hello"_, the agent shouldn't need to pull in 20,000 tokens of previous context, load all of the tool definitions, and spin up a high-power reasoning model just to respond. A lightweight model with limited context and no tools defined can answer with _"Hello! How can I help you today?"_ — saving cost and latency for the cases that actually need the full agent.

##### Decision Tables

In some cases the "decision" we want the agent to make is really just a **lookup**. Instead of having the LLM reason over account details and policies on every request, we can encode that logic in a table.

This sits in the middle of the techniques we've covered: where **Rule-Based Validation** is about guardrails and **Intent Classification** is about figuring out what kind of request we have, **Decision Tables** are something in between. We can think of them as a more complex decision tree — given this combination of factors, what should we do?

For example, if we're talking to a verified user whose account details we know, and they have a certain account age and purchase history, we might choose to reduce friction for our local customers and **automatically approve refunds under $20**. Decision tables are particularly powerful because they let us evaluate **multiple conditions simultaneously** and resolve them into a single action.

Decision tables are easy to read, easy to test, and easy to update by people who don't write LLM prompts. They pair particularly well with Intent Classification — the classifier turns the user's free-form message into a discrete category, and the decision table turns that category (plus context) into a deterministic plan of action.

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

#### Exercise

See [Exercise TODO](./java/TODO.md).
