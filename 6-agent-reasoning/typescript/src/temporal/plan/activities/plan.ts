import { PromptTemplate } from "@langchain/core/prompts";
import { ChatOpenAI } from "@langchain/openai";
import { Config } from "../../../config";
import { structuredToolsXML } from "../../../tools";
import { required } from "zod/mini";

export type PlanInput = {
  context: string[];
};

export type PlanStep = {
  id: number;
  tool_name: string;
  tool_input: Record<string, any>;
  dependsOn: number[];
};

export type PlanOutput = {
  steps: PlanStep[];
};

const PlanStructuredOutput = {
  type: "object",
  properties: {
    steps: {
      type: "array",
      items: {
        type: "object",
        properties: {
          id: {
            type: "number",
            description:
              "Unique identifier for the step. Used to reference this step in dependencies and results.",
          },
          tool_name: {
            type: "string",
            description: "Name of the tool to be used in this step.",
          },
          tool_input: {
            type: "object",
            additionalProperties: false,
            description:
              "Input for the tool, matching the tool's input schema. If you need a result from another step as a value, use the syntax {{result:[id]}} where [id] is the step Id.",
          },
          dependsOn: {
            type: "array",
            items: {
              type: "number",
              description:
                "List of step IDs that this step depends on. If this step does not depend on any other steps, this list should be empty.",
            },
          },
        },
        required: ["id", "tool_name", "tool_input", "dependsOn"],
      },
    },
  },
  required: ["steps"],
};

export async function Plan(input: PlanInput): Promise<PlanOutput> {
  const availableTools = structuredToolsXML();
  const promptTemplate = planPromptTemplate();

  const base = new ChatOpenAI({
    model: Config.OPENAI_HIGH_MODEL,
    apiKey: Config.OPENAI_API_KEY,
    streaming: false,
  });

  const formattedPrompt = await promptTemplate.format({
    previousSteps: input.context.join("\n"),
    availableActions: availableTools,
  });

  const model = base.withStructuredOutput(PlanStructuredOutput);

  const response = await model.invoke([
    { role: "user", content: formattedPrompt },
  ]);

  return createPlanOutput(response);
}

function createPlanOutput(output: Record<string, any>): PlanOutput {
  console.debug(
    "Creating plan output from model response:",
    JSON.stringify(output),
  );

  if (!output.steps) {
    throw new Error("Invalid output: Missing 'steps' property");
  }

  if (!Array.isArray(output.steps)) {
    throw new Error("Invalid output: 'steps' should be an array");
  }

  const steps: PlanStep[] = output.steps.map((step) => {
    if (!step.id || !step.tool_name || !step.tool_input || !step.dependsOn) {
      throw new Error("Invalid output: Step is missing required fields");
    }

    if (!Array.isArray(step.dependsOn)) {
      throw new Error("Invalid output: dependsOn should be an array");
    }

    return {
      id: step.id,
      tool_name: step.tool_name,
      tool_input: step.tool_input,
      dependsOn: step.dependsOn,
    };
  });

  return {
    steps,
  };
}

export function planPromptTemplate() {
  const templateString = `You are the Planner for a Plan and Execute AI Agent. Your job is to create a plan to answer the user's query.

Your plan should consist of a series of steps, each using one of the available actions to gather information or perform tasks. You must create a dependency graph of the steps,
showing which steps depend on the results of previous steps.

When creating the Steps, follow these rules:
- Each step must use one of the available actions.
- If a step requires information from a previous step, it must reference that step's Id in its dependsOn list.
- The tool_input for each step must match the input schema of the tool being used. 
    - If you need a result from another step as a value, use the syntax {{result:[id]}} where [id] is the step Id. This {{result:[id]}} will be replaced with the actual result during execution.

Here is the context from the conversation so far: 
<context>
{previousSteps}
</context>

Here is the list of available actions you can use in your plan:
<availableActions>
{availableActions}
</availableActions>
`;

  const prompt = new PromptTemplate({
    template: templateString,
    inputVariables: ["previousSteps", "availableActions"],
  });

  return prompt;
}
