/**
 * Strip chain-of-thought reasoning that Bedrock sometimes leaks into the final answer.
 */
export function cleanFinalAnswer(text: string): string {
  return text.trim();
}

/**
 * Sanitize tool input for display — strip sensitive identity fields.
 */
export function sanitizeInput(input: Record<string, any>): Record<string, any> {
  const sanitized = { ...input };
  delete sanitized.email;
  delete sanitized.payment_last4;
  return sanitized;
}
