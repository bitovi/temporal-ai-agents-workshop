import { StructuredTool, tool } from "@langchain/core/tools";
import { Config } from "./config";
import z from "zod";
import { isProbablyReaderable, Readability } from "@mozilla/readability";
import { JSDOM } from "jsdom";

export function structuredTools(): StructuredTool[] {
  const result = [
    braveSearch(),
    fetchWebpage(),
    calculatorAdd(),
    calculatorSubtract(),
    calculatorMultiply(),
    calculatorDivide(),
  ];
  return result;
}

export function structuredToolsXML(): string {
  const list = structuredTools();

  const tools = list.map((tool) => {
    if (tool.schema instanceof z.ZodType) {
      return `<tool>
    <name>${tool.name}</name>
    <description>${tool.description}</description>
    <schema>${JSON.stringify(z.toJSONSchema(tool.schema))}</schema>
</tool>`;
    }

    return `<tool>
    <name>${tool.name}</name>
    <description>${tool.description}</description>
    <schema>${JSON.stringify(tool.schema)}</schema>
</tool>`;
  });

  const result = tools.join("\n");
  return result;
}

function braveSearch(): StructuredTool {
  return tool(
    async (input: Record<string, string>) => {
      const params = new URLSearchParams();
      for (const key in input) {
        params.append(key, input[key]);
      }

      const options = {
        method: "GET",
        headers: {
          Accept: "application/json",
          "Accept-Encoding": "gzip",
          "x-subscription-token": Config.BRAVE_SEARCH_API_KEY,
        },
      };

      const result = await fetch(
        `https://api.search.brave.com/res/v1/web/search?${params}`,
        options,
      );
      const data = await result.json();
      return JSON.stringify(data);
    },
    {
      name: "brave_search",
      description: "Search the Internet using the Brave Search Engine API.",
      schema: z.object({
        q: z.string().describe("The search query string."),
        count: z
          .number()
          .optional()
          .describe("Number of results to return. Defaults to 10."),
      }),
    },
  );
}

function fetchWebpage(): StructuredTool {
  return tool(
    async (input: { url: string }) => {
      const response = await fetch(input.url);
      const page = await response.text();

      const extracted = extractReadable(page, input.url) || page;

      console.debug(
        `Extracted content length: ${extracted.length} characters.`,
      );

      return extracted;
    },
    {
      name: "fetch_webpage",
      description:
        "Fetch the content of a webpage given its URL. Uses a simple GET request with node `fetch`. No JavaScript execution. Attempts to extract the main content with Mozilla Readability.",
      schema: z.object({
        url: z.string().describe("The URL of the webpage to fetch."),
      }),
    },
  );
}

function calculatorAdd(): StructuredTool {
  return tool(
    async (input: { a: number; b: number }) => {
      return input.a + input.b;
    },
    {
      name: "calculator_add",
      description: "Add two numbers together.",
      schema: z.object({
        a: z.number().describe("The first number."),
        b: z.number().describe("The second number."),
      }),
    },
  );
}

function calculatorSubtract(): StructuredTool {
  return tool(
    async (input: { a: number; b: number }) => {
      return input.a - input.b;
    },
    {
      name: "calculator_subtract",
      description: "Subtract two numbers.",
      schema: z.object({
        a: z.number().describe("The first number."),
        b: z.number().describe("The second number."),
      }),
    },
  );
}

function calculatorMultiply(): StructuredTool {
  return tool(
    async (input: { a: number; b: number }) => {
      return input.a * input.b;
    },
    {
      name: "calculator_multiply",
      description: "Multiply two numbers.",
      schema: z.object({
        a: z.number().describe("The first number."),
        b: z.number().describe("The second number."),
      }),
    },
  );
}

function calculatorDivide(): StructuredTool {
  return tool(
    async (input: { numerator: number; denominator: number }) => {
      if (input.denominator === 0) {
        throw new Error("Denominator cannot be zero.");
      }

      return input.numerator / input.denominator;
    },
    {
      name: "calculator_divide",
      description: "Divide two numbers.",
      schema: z.object({
        numerator: z.number().describe("The numerator."),
        denominator: z.number().describe("The denominator."),
      }),
    },
  );
}

function extractReadable(html: string, url: string): string | null {
  const dom = new JSDOM(html, { url });
  if (!isProbablyReaderable(dom.window.document)) {
    return null;
  }

  const readable = new Readability(dom.window.document);
  const parsed = readable.parse();
  if (!parsed) {
    return null;
  }

  const parts = [parsed.title, parsed.byline, parsed.textContent]
    .filter(Boolean)
    .join("\n\n");

  // Basic sanity: require some length
  if (parts.split(/\s+/).length < 50) return null; // too short, maybe extraction failed

  console.debug(
    `Extracted readable content length: ${parts.length} characters.`,
  );

  return parts;
}
