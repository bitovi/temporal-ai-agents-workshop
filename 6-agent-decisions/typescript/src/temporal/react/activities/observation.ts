import { PromptTemplate } from "@langchain/core/prompts";
import { ChatOpenAI } from "@langchain/openai";
import { Config } from "../../../config";
import {
  AIMessageChunk,
  MessageStructure,
  MessageToolSet,
} from "@langchain/core/messages";

export type ReactObservationInput = {
  reasoning: string;
  action: {
    name: string;
    input: string | object;
  };
  result: string;
};

export type ReactObservationOutput = {
  result: string;
};

export async function ReactObservation(
  input: ReactObservationInput,
): Promise<ReactObservationOutput> {
  const promptTemplate = observationPromptTemplate();

  const model = new ChatOpenAI({
    model: Config.OPENAI_LOW_MODEL,
    apiKey: Config.OPENAI_API_KEY,
    streaming: false,
  });

  const formattedPrompt = await promptTemplate.format({
    reasoning: input.reasoning,
    actionName: input.action.name,
    actionInput: input.action.input,
    actionResult: input.result,
  });

  const response = await model.invoke([
    { role: "user", content: formattedPrompt },
  ]);

  return createReactObservationOutput(response);
}

function createReactObservationOutput(
  response: AIMessageChunk<MessageStructure<MessageToolSet>>,
): ReactObservationOutput {
  if (!response.content) {
    throw new Error("Response content is empty");
  }

  if (typeof response.content !== "string") {
    throw new Error("Response content is not a string");
  }

  return {
    result: response.content,
  };
}

export function observationPromptTemplate() {
  const templateString = `You are a ReAct (Reasoning and Acting) agent tasked with answering user queries.

Your goal is to extract insights from the results of your last action and provide a concise observation.

Instructions:
1. Analyze the query, previous reasoning steps, and observations.
2. Extract insights from the latest action result.
3. Respond with a concise observation that summarizes the results of the last action taken.

You do not need to include any XML tags such as <thought>, <action>, or <observation> in your response, those will be added automatically by the Agent Workflow.

The last action was selected due to this reasoning: 

{reasoning}

Provide your observation based on the latest action:

<action-name>
{actionName}
</action-name>

<action-input>
{actionInput}
</action-input>

<action-result>
{actionResult}
</action-result>
`;

  const prompt = new PromptTemplate({
    template: templateString,
    inputVariables: ["reasoning", "actionName", "actionInput", "actionResult"],
  });

  return prompt;
}
