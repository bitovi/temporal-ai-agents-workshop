export type PlanResponseInput = {
  steps: any[];
  results: any[];
};

export type PlanResponseOutput = {
  result: string;
};

export async function PlanResponse(
  input: PlanResponseInput,
): Promise<PlanResponseOutput> {
  return {
    result: `Final Response:\n${input.steps.map((step) => step.result).join("\n")}`,
  };
}
