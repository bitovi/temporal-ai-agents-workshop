import { 
  BedrockRuntimeClient, 
  ConverseCommand, 
  Tool, 
  Message, 
  ContentBlock 
} from '@aws-sdk/client-bedrock-runtime';

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
}

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

/**
 * AWS Bedrock client for conversational AI interactions.
 * 
 * Token Limit: Maximum 12,000 tokens per request.
 * Token Estimation Heuristic: 1 token ≈ 4 characters
 * 
 * Example: A 48,000 character message would be approximately 12,000 tokens (at the limit).
 */
const client = new BedrockRuntimeClient({
  region: process.env.AWS_REGION as string,
  credentials: {
    accessKeyId: process.env.AWS_ACCESS_KEY_ID || '',
    secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY || '',
    sessionToken: process.env.AWS_SESSION_TOKEN || undefined,
  },
});

/**
 * Calls AWS Bedrock Converse API with a message history.
 * 
 * @param messages - Array of chat messages with role and content
 * @param systemPrompt - Optional system prompt to set agent behavior
 * @returns The assistant's response text
 * @throws Error if API call fails or credentials are invalid
 */
export async function callBedrock(
  messages: ChatMessage[],
  systemPrompt?: string
): Promise<string> {
  console.log(`[Bedrock] Sending ${messages.length} message(s) to model ${process.env.AWS_MODEL_ID}`);
  
  // Estimate token count using heuristic: 1 token ≈ 4 characters
  const totalChars = messages.reduce((sum, msg) => sum + msg.content.length, 0);
  const estimatedTokens = Math.ceil(totalChars / 4);
  console.log(`[Bedrock] Estimated tokens: ${estimatedTokens} (~${totalChars} characters)`);
  
  if (estimatedTokens > 12000) {
    console.warn(`[Bedrock] WARNING: Estimated tokens (${estimatedTokens}) exceeds 12,000 token limit`);
  }

  const command = new ConverseCommand({
    modelId: process.env.AWS_MODEL_ID as string,
    system: systemPrompt ? [{ text: systemPrompt }] : undefined,
    messages: messages.map((msg) => ({
      role: msg.role,
      content: [{ text: msg.content }],
    })),
  });

  try {
    const response = await client.send(command);
    
    // Extract the text from the response
    const outputMessage = response.output?.message;
    if (!outputMessage || !outputMessage.content || outputMessage.content.length === 0) {
      throw new Error('Empty response from Bedrock API');
    }

    // Find the text content block (skip reasoning blocks if present)
    const textContent = outputMessage.content.find((block) => 'text' in block);
    
    if (!textContent || !('text' in textContent) || !textContent.text) {
      throw new Error('No text content in Bedrock response');
    }

    const responseText = textContent.text;
    console.log(`[Bedrock] Received response: ${responseText.substring(0, 100)}...`);
    
    return responseText;
  } catch (error) {
    console.error('[Bedrock] API call failed:', error);
    throw error;
  }
}

/**
 * Calls AWS Bedrock Converse API with tool calling support.
 * 
 * @param messages - Array of Bedrock Message objects with ContentBlock arrays
 * @param systemPrompt - System prompt to set agent behavior
 * @param tools - Array of tool specifications for Bedrock
 * @returns BedrockResponse with text, toolUses, or both
 * @throws Error if API call fails or credentials are invalid
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

    // Extract text content blocks
    const textBlocks = outputMessage.content.filter((block) => block.text);
    if (textBlocks.length > 0) {
      result.text = textBlocks.map((block) => block.text).join('\n');
      console.log(`[Bedrock] Received text response: ${result.text.substring(0, 100)}...`);
    }

    // Extract tool use blocks
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
