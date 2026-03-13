import {
  executeLookupAccount,
  executeCheckBillingHistory,
  executeVerifyIdentity,
  executeProcessRefund,
} from './support-tools';

/**
 * Execute a tool by name with the provided input parameters.
 * Routes to the appropriate execution function.
 * 
 * Note: request_verification is a sentinel tool — it is never routed here.
 * The server.ts ReAct loop intercepts it before execution.
 */
export function executeTool(
  toolName: string,
  toolInput: Record<string, any>
): string {
  console.log(`[ToolExecutor] Executing tool: ${toolName}`);
  console.log(`[ToolExecutor] Input:`, JSON.stringify(toolInput, null, 2));

  try {
    switch (toolName) {
      case 'lookup_account':
        return executeLookupAccount(toolInput);
      
      case 'check_billing_history':
        return executeCheckBillingHistory(toolInput);
      
      case 'verify_identity':
        return executeVerifyIdentity(toolInput);
      
      case 'process_refund':
        return executeProcessRefund(toolInput);
      
      default:
        const errorMsg = `Unknown tool: ${toolName}`;
        console.error(`[ToolExecutor] ${errorMsg}`);
        return JSON.stringify({ error: errorMsg });
    }
  } catch (error) {
    const errorMsg = `Tool execution failed: ${error instanceof Error ? error.message : 'Unknown error'}`;
    console.error(`[ToolExecutor] ${errorMsg}`, error);
    return JSON.stringify({ error: errorMsg });
  }
}
