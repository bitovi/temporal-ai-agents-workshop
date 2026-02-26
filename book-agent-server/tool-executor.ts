import { 
  executeSearchBooks, 
  executeGetBookById, 
  GutendexSearchParams, 
  GutendexBookIdParams 
} from './gutendex-tools';

/**
 * Execute a tool by name with the provided input parameters.
 * Routes to the appropriate execution function.
 * 
 * @param toolName Name of the tool to execute
 * @param toolInput Input parameters for the tool
 * @returns Tool result as a JSON string
 */
export async function executeTool(
  toolName: string,
  toolInput: Record<string, any>
): Promise<string> {
  console.log(`[ToolExecutor] Executing tool: ${toolName}`);
  console.log(`[ToolExecutor] Input:`, JSON.stringify(toolInput, null, 2));

  try {
    switch (toolName) {
      case 'search_books':
        return await executeSearchBooks(toolInput as GutendexSearchParams);
      
      case 'get_book_by_id':
        // Validate required parameter
        if (typeof toolInput.id !== 'number') {
          return JSON.stringify({
            error: 'Invalid parameter: id must be a number'
          });
        }
        return await executeGetBookById(toolInput as GutendexBookIdParams);
      
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
