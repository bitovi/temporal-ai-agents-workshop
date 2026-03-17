import {
  executeLookupAccount,
  executeCheckBillingHistory,
  executeVerifyIdentity,
  executeApplyResolution,
} from './support-tools';

/**
 * Routes tool names to their executor functions.
 *
 * Note: sentinel tools (request_verification, request_information,
 * offer_resolution_options) are intercepted by server.ts BEFORE reaching
 * this router. They trigger `input-required` status instead of executing.
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
      
      case 'apply_resolution':
        return executeApplyResolution(toolInput);
      
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
