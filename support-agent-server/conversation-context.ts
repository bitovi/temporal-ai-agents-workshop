import { Message, ContentBlock } from "@aws-sdk/client-bedrock-runtime";

/**
 * Manages conversation context using Bedrock's native Message format.
 *
 * Bedrock requires strict alternating user/assistant roles. This class
 * handles that constraint, including the tricky case of tool results
 * (which must be user-role messages containing toolResult blocks).
 *
 * When the agent pauses for input (input-required), we inject a synthetic
 * assistant message so the next user message doesn't violate the alternation.
 */
export class ConversationContext {
  private messages: Message[] = [];

  addUserMessage(text: string): void {
    this.messages.push({
      role: "user",
      content: [{ text }],
    });
  }

  addAssistantMessage(content: ContentBlock[]): void {
    this.messages.push({
      role: "assistant",
      content,
    });
  }

  addToolResult(toolUseId: string, content: string): void {
    const lastMessage = this.messages[this.messages.length - 1];

    if (
      lastMessage &&
      lastMessage.role === "user" &&
      lastMessage.content?.some((block) => block.toolResult)
    ) {
      lastMessage.content!.push({
        toolResult: {
          toolUseId,
          content: [{ text: content }],
        },
      });
    } else {
      this.messages.push({
        role: "user",
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

  getMessages(): Message[] {
    return this.messages;
  }

  clear(): void {
    this.messages = [];
  }

  estimateTokenCount(): number {
    let totalChars = 0;

    for (const message of this.messages) {
      for (const block of message.content || []) {
        if (block.text) {
          totalChars += block.text.length;
        } else if (block.toolUse) {
          totalChars += JSON.stringify(block.toolUse.input).length;
        } else if (block.toolResult) {
          totalChars += JSON.stringify(block.toolResult.content).length;
        }
      }
    }

    return Math.ceil(totalChars / 4);
  }

  truncateOldest(): void {
    if (this.messages.length <= 2) {
      console.warn("[ConversationContext] Cannot truncate - too few messages");
      return;
    }

    if (this.messages.length >= 5) {
      console.log("[ConversationContext] Truncating oldest message pair");
      this.messages.splice(1, 2);
    }
  }
}
