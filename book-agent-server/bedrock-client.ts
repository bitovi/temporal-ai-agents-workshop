import { BedrockRuntimeClient, ConverseCommand } from '@aws-sdk/client-bedrock-runtime';

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
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
