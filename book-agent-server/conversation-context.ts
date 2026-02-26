import { Message, ContentBlock } from '@aws-sdk/client-bedrock-runtime';

/**
 * Manages conversation context using Bedrock's native Message format.
 * Maintains ordered list of user/assistant messages with proper tool calling pattern.
 */
export class ConversationContext {
  private messages: Message[] = [];

  /**
   * Add a user message with text content
   */
  addUserMessage(text: string): void {
    this.messages.push({
      role: 'user',
      content: [{ text }],
    });
  }

  /**
   * Add an assistant message with content blocks (text, tool use, etc.)
   */
  addAssistantMessage(content: ContentBlock[]): void {
    this.messages.push({
      role: 'assistant',
      content,
    });
  }

  /**
   * Add a tool result as a user message
   * Follows Bedrock's pattern: assistant requests tool -> user provides result
   */
  addToolResult(toolUseId: string, content: string): void {
    // Tool results are sent as user messages with toolResult blocks
    // If the last message is already a user message with tool results, append to it
    // Otherwise, create a new user message
    const lastMessage = this.messages[this.messages.length - 1];
    
    if (lastMessage && lastMessage.role === 'user' && lastMessage.content?.some(block => block.toolResult)) {
      // Append to existing user message with tool results
      lastMessage.content!.push({
        toolResult: {
          toolUseId,
          content: [{ text: content }],
        },
      });
    } else {
      // Create new user message with tool result
      this.messages.push({
        role: 'user',
        content: [
          {
            toolResult: {
              toolUseId,
              content: [{ text: content }],
            },
          },
        ],
      });
    }
  }

  /**
   * Get all messages for Bedrock API call
   */
  getMessages(): Message[] {
    return this.messages;
  }

  /**
   * Clear all messages
   */
  clear(): void {
    this.messages = [];
  }

  /**
   * Estimate token count using heuristic: 1 token ≈ 4 characters
   * Counts all text content in messages
   */
  estimateTokenCount(): number {
    let totalChars = 0;
    
    for (const message of this.messages) {
      for (const block of message.content || []) {
        if (block.text) {
          totalChars += block.text.length;
        } else if (block.toolUse) {
          totalChars += JSON.stringify(block.toolUse.input).length;
        } else if (block.toolResult) {
          // toolResult.content can be an array of ContentBlocks or other structures
          // For simplicity, stringify the entire content
          totalChars += JSON.stringify(block.toolResult.content).length;
        }
      }
    }
    
    return Math.ceil(totalChars / 4);
  }

  /**
   * Truncate oldest message pair (user + assistant) to reduce token count
   * Preserves the initial user question
   */
  truncateOldest(): void {
    if (this.messages.length <= 2) {
      // Don't truncate if we only have initial question and one response
      console.warn('[ConversationContext] Cannot truncate - too few messages');
      return;
    }

    // Find the first non-initial message pair to remove
    // Keep index 0 (initial user message)
    // Remove indices 1 and 2 (first assistant response and subsequent user message)
    if (this.messages.length >= 3) {
      console.log('[ConversationContext] Truncating oldest message pair');
      this.messages.splice(1, 2);
    }
  }
}
