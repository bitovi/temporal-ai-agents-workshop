import { 
  BedrockRuntimeClient, 
  ConverseCommand, 
  Tool, 
  Message, 
  ContentBlock 
} from '@aws-sdk/client-bedrock-runtime';

export interface ToolUse {
  toolUseId: string;
  name: string;
  input: Record<string, any>;
}

export interface BedrockResponse {
  text?: string;
  toolUses?: ToolUse[];
  stopReason?: string;
}

const client = new BedrockRuntimeClient({
  region: process.env.AWS_REGION as string,
  credentials: {
    accessKeyId: process.env.AWS_ACCESS_KEY_ID || '',
    secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY || '',
    sessionToken: process.env.AWS_SESSION_TOKEN || undefined,
  },
});

/**
 * Call Bedrock Converse API with tool definitions.
 *
 * This is the core LLM call used by the support agent's ReAct loop.
 * Bedrock's Converse API natively supports tool use: when the model wants
 * to call a tool, it returns a `toolUse` content block instead of (or in
 * addition to) text.
 */
export async function callBedrockWithTools(
  messages: Message[],
  systemPrompt: string,
  tools: Tool[]
): Promise<BedrockResponse> {
  console.log(`[Bedrock] Sending ${messages.length} message(s) with ${tools.length} tool(s) to model ${process.env.AWS_MODEL_ID}`);

  const command = new ConverseCommand({
    modelId: process.env.AWS_MODEL_ID as string,
    system: [{ text: systemPrompt }],
    messages: messages,
    toolConfig: {
      tools: tools,
    },
  });

  try {
    const response = await client.send(command);
    
    const outputMessage = response.output?.message;
    if (!outputMessage || !outputMessage.content || outputMessage.content.length === 0) {
      console.warn('[Bedrock] Empty response from Bedrock API');
      return { stopReason: response.stopReason };
    }

    const result: BedrockResponse = {
      stopReason: response.stopReason,
    };

    const textBlocks = outputMessage.content.filter((block) => block.text);
    if (textBlocks.length > 0) {
      result.text = textBlocks.map((block) => block.text).join('\n');
      console.log(`[Bedrock] Received text response: ${result.text.substring(0, 100)}...`);
    }

    const toolUseBlocks = outputMessage.content.filter((block) => block.toolUse);
    if (toolUseBlocks.length > 0) {
      result.toolUses = toolUseBlocks.map((block) => ({
        toolUseId: block.toolUse!.toolUseId!,
        name: block.toolUse!.name!,
        input: block.toolUse!.input as Record<string, any>,
      }));
      console.log(`[Bedrock] Received ${result.toolUses.length} tool use request(s)`);
    }

    return result;
  } catch (error) {
    console.error('[Bedrock] API call failed:', error);
    throw error;
  }
}
