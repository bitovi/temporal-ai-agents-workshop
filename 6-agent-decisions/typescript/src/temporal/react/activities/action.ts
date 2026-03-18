import { StructuredTool } from "@langchain/core/tools";
import { structuredTools } from "../../../tools";

export type ReactActionInput = {
  name: string;
  input: string | object;
};

export type ReactActionOutput = {
  result: string;
  error: boolean;
};

export async function ReactAction(
  input: ReactActionInput,
): Promise<ReactActionOutput> {
  const tools: StructuredTool[] = structuredTools();
  const tool = tools.find((t) => t.name === input.name);
  if (!tool) {
    return {
      result: `Tool '${input.name}' not found.`,
      error: true,
    };
  }

  try {
    const result = await tool.invoke(input.input);
    return {
      result: result,
      error: false,
    };
  } catch (err: unknown) {
    const error = err instanceof Error ? err : new Error(String(err));
    return {
      result: `Error invoking tool '${input.name}': ${error.message}`,
      error: true,
    };
  }
}
