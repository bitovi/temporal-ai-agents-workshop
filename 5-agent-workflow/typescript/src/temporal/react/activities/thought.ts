import { PromptTemplate } from "@langchain/core/prompts";
import { ChatOpenAI } from "@langchain/openai";
import { Config } from "../../../config";
import { structuredToolsXML } from "../../../tools";

export type ReactThoughtInput = {
  context: string[];
};

export type ReactThoughtOutput = {
  reasoning: string;
  answer?: string;
  action?: {
    name: string;
    input: string | object;
  };
};

const ReactThoughtStructuredOutput = {
  type: "object",
  additionalProperties: false,
  properties: {
    reasoning: {
      type: "string",
    },
    action: {
      type: "object",
      additionalProperties: false,
      properties: {
        name: {
          type: "string",
        },
        input: {
          type: "object",
          additionalProperties: true,
        },
      },
      required: ["name", "input"],
    },
    answer: {
      type: "string",
    },
  },
  required: ["reasoning"],
};

export async function ReactThought(
  input: ReactThoughtInput,
): Promise<ReactThoughtOutput> {
  const availableTools = structuredToolsXML();
  const promptTemplate = thoughtPromptTemplate();

  const base = new ChatOpenAI({
    model: Config.OPENAI_HIGH_MODEL,
    apiKey: Config.OPENAI_API_KEY,
    streaming: false,
  });

  const formattedPrompt = await promptTemplate.format({
    currentDate: new Date().toISOString().split("T")[0],
    previousSteps: input.context.join("\n"),
    availableActions: availableTools,
  });

  const model = base.withStructuredOutput(ReactThoughtStructuredOutput);

  const response = await model.invoke([
    { role: "user", content: formattedPrompt },
  ]);

  return createReactThoughtOutput(response);
}

function createReactThoughtOutput(
  output: Record<string, any>,
): ReactThoughtOutput {
  if (!output.reasoning) {
    throw new Error("Invalid output: missing 'reasoning' field");
  }

  if (!output.action && !output.answer) {
    throw new Error("Invalid output: missing 'action' or 'answer' field");
  }

  if (output.answer) {
    console.log("Answer output:", output.answer);
    return {
      reasoning: output.reasoning,
      answer: output.answer,
    };
  }

  if (output.action) {
    if (!output.action.name || !output.action.input) {
      console.log("Invalid action output:", output.action);
      throw new Error(
        "Invalid output: missing 'name' or 'input' field in 'action'",
      );
    }

    console.log("Action output:", output.action);
    return {
      reasoning: output.reasoning,
      action: output.action,
    };
  }

  throw new Error("Invalid output: missing 'action' or 'answer' field");
}

export function thoughtPromptTemplate() {
  const templateString = `You are a ReAct (Reasoning and Acting) agent tasked with answering user queries.

Your goal is to reason about the query and decide on the best course of action to answer it accurately.

Instructions:
1. Analyze the query, previous reasoning steps, and observations.
2. Decide on the next action: use a tool or provide a final answer.
3. Respond in the following JSON format:

If you need to use a tool:
{{
    "thought": "Your detailed reasoning about what to do next",
    "action": {{
        "name": "EXACT tool name from the available actions below",
        "reason": "Explanation of why you chose this tool",
        "input": "JSON object matching to tool input schema"
    }}
}}

If you have enough information to answer the query:
{{
    "thought": "Your final reasoning process",
    "answer": "Your comprehensive answer to the query"
}}

IMPORTANT RULES:
- When selecting a tool, you MUST use the exact name from the list of available actions below. Never use an empty string or a name not in the available actions list.
- The "name" field in your action MUST be one of the tool names listed in the <available-actions> section.
- Be thorough in your reasoning.
- Use tools when you need more information.
- Use tools to validate your assumptions and internal knowledge.
- Be sure to match the tool input schema exactly.
- Always base your reasoning on the actual observations from tool use.
- If a tool returns no results or fails, acknowledge this and consider using a different tool or approach.
- Provide a final answer only when you're confident you have sufficient information.
- If you cannot find the necessary information after using available tools, admit that you don't have enough information to answer the query confidently.
- Your internal knowledge may be outdated. The current date is {currentDate}.

You do not need to include any XML tags such as <thought>, <action>, or <observation> in your response, those will be added automatically by the Agent Workflow.

In this thinking step, consider the following information from previous steps:

<previous-steps>
{previousSteps}
</previous-steps>

Based on that information, provide your thought process and decide on the next action.

AVAILABLE TOOLS - Choose the "name" field from one of these exact tool names:
<available-actions>
{availableActions}
</available-actions>
`;

  const prompt = new PromptTemplate({
    template: templateString,
    inputVariables: ["currentDate", "previousSteps", "availableActions"],
  });

  return prompt;
}
