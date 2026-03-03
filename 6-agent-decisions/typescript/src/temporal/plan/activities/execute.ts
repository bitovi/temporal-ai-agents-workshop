import { StructuredTool } from "@langchain/core/tools";
import type { PlanStep } from "./plan";
import { structuredTools } from "../../../tools";

export type ExecuteStepInput = {
  step: PlanStep;
  dependsOn: { id: number; result: string }[];
};

export type ExecuteStepOutput = {
  result: string;
};

/**
{
  "step": {
    "id": 2,
    "tool_name": "calculator_divide",
    "tool_input": {
      "numerator": "{{result:1}}",
      "denominator": 37
    },
    "dependsOn": [
      1
    ]
  },
  "dependsOn": [
    {
      "id": 1,
      "result": {
        "result": 2404.1
      }
    }
  ]
}
 */

export async function ExecuteStep(
  input: ExecuteStepInput,
): Promise<ExecuteStepOutput> {
  const tools: StructuredTool[] = structuredTools();
  const tool = tools.find((t) => t.name === input.step.tool_name);
  if (!tool) {
    throw new Error(`Tool '${input.step.tool_name}' not found.`);
  }

  console.log("Executing tool:", input.step.tool_name);
  console.log("Tool input before substitution:", input.step.tool_input);

  // TODO: Perform substitutions for {{result:[id]}} in input.step.tool_input
  // using the values we have in our dependency list input.dependsOn[{id: number, result: string}]

  const keys = Object.keys(input.step.tool_input);
  keys.forEach((key) => {
    const value = input.step.tool_input[key];
    if (typeof value == "string") {
      // Check if value contains {{result:}} anywhere in the string.
      // The value could be just '{{result:}}' or a part of a larger string like 'search the web for {{result:}}'
      const match = value.matchAll(/{{result:(\d+)}}/g);
      for (const m of match) {
        // For each match, replace the {{result:[id]}} with the actual result from input.dependsOn
        const id = parseInt(m[1]);
        const dependency = input.dependsOn.find((d) => d.id === id);
        if (!dependency) {
          throw new Error(`Dependency with id ${id} not found in dependsOn`);
        }
        console.log(
          `Replacing ${m[0]} with ${dependency.result} in key ${key}`,
        );

        if (value === m[0]) {
          // If the string is '{{result:}}' we should use the result as is, to preserve things like numbers.
          input.step.tool_input[key] = dependency.result;
        } else {
          // If the string is part of a larger string, we should replace just the placeholder.
          input.step.tool_input[key] = value.replace(m[0], dependency.result);
        }
      }
    }
  });

  console.log("Tool input after substitution:", input.step.tool_input);
  const result = await tool.invoke(input.step.tool_input);
  return { result };
}
