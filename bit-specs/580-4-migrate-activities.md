# SYSTEMS-580-4: Migrate Activities

This is a subsection of [580-0-convert-ts-to-java.md](./580-0-convert-ts-to-java.md) - see that spec for overall context.

## Overview

Migrate the temporal activities used in the agent workflow from stubbed implementations to fully functional versions. The activities need to integrate with AWS Bedrock for AI interactions, implement prompt templates, handle tool execution, and manage conversation context.

## Context

**Reference Implementation:** [temp-ref-code/src/workflows/activities.ts](../temp-ref-code/src/workflows/activities.ts)

**Target Files:**
- [5-agent-workflow/java/src/main/java/bitovi/activities/ActivitiesImpl.java](../5-agent-workflow/java/src/main/java/bitovi/activities/ActivitiesImpl.java)
- [5-agent-workflow/java/src/main/java/bitovi/common/AWS.java](../5-agent-workflow/java/src/main/java/bitovi/common/AWS.java)

**Reference Examples:**
- [1-prompt-engineering/java/src/main/java/bitovi/activities/ActivitiesImpl.java](../1-prompt-engineering/java/src/main/java/bitovi/activities/ActivitiesImpl.java) - Basic Bedrock integration
- [3-tool-calling/java/src/main/java/bitovi/activities/ActivitiesImpl.java](../3-tool-calling/java/src/main/java/bitovi/activities/ActivitiesImpl.java) - Tool execution patterns
- [3-tool-calling/java/src/main/java/bitovi/common/AWS.java](../3-tool-calling/java/src/main/java/bitovi/common/AWS.java) - Bedrock converse with tools

**Key TypeScript References:**
- [temp-ref-code/src/workflows/prompts.ts](../temp-ref-code/src/workflows/prompts.ts) - Prompt templates
- [temp-ref-code/src/workflows/activities.ts](../temp-ref-code/src/workflows/activities.ts) - Activity implementations
- [temp-ref-code/src/internals/model.ts](../temp-ref-code/src/internals/model.ts) - Model interaction and token handling
- [temp-ref-code/src/internals/tools.ts](../temp-ref-code/src/internals/tools.ts) - Tool enumeration

## Implementation Plan

### Step 1: Create Prompt Templates in Resources

**Goal:** Create prompt template files that can be loaded by Java activities.

**Actions:**
1. Create `src/main/resources/prompts/` directory structure
2. Create `thought-prompt.txt` with the ReAct agent prompt from TypeScript `thoughtPromptTemplate()`
   - Include placeholders: `{currentDate}`, `{previousSteps}`, `{availableActions}`
   - Preserve XML-like tags and JSON format instructions
3. Create `observation-prompt.txt` from `observationPromptTemplate()`
   - Include placeholders: `{previousSteps}`, `{actionResult}`
4. Create `compact-prompt.txt` from `compactPromptTemplate()`
   - Include placeholder: `{contextHistory}`

**Verification:**
- All three prompt files exist in `src/main/resources/prompts/`
- Placeholders match the format strings that will be used in Java
- Prompts preserve the original intent and structure from TypeScript

### Step 2: Create Tool Registry Infrastructure

**Goal:** Implement tool enumeration and registration system similar to TypeScript `fetchStructuredTools()`.

**Actions:**
1. Create `bitovi.activities.tools` package
2. Create abstract `Tool` interface/class:
   - Method signatures: `static Tool getBedrockTool()`, `String execute(Map<String, Object> input)`
   - The `getBedrockTool()` method returns the complete AWS SDK `Tool` object with `ToolSpecification` including schema
   - Reference existing pattern: [3-tool-calling WeatherTool](../3-tool-calling/java/src/main/java/bitovi/activities/tools/WeatherTool.java)
3. Create `BraveSearchTool` class:
   - Implement HTTP call to Brave Search API using standard Java HTTP client
   - Use Config to get `BRAVE_SEARCH_API_KEY`
   - Schema: `q` (string, required), `count` (number, optional)
   - Return JSON string result (or generic response if Brave API format unknown)
   - Reference TypeScript implementation in [temp-ref-code](../temp-ref-code/src/internals/tools.ts) if available
4. Create `FetchWebpageTool` class:
   - Implement simple HTTP GET request
   - Schema: `url` (string, required)
   - Return webpage content as text
5. Note on existing tools:
   - Keep existing tools (`FetchAccountInfo.java`, `QuestionAnswered.java`, `WeatherTool.java`, `SearchWeb.java`) in place
   - Focus implementation on `BraveSearchTool` and `FetchWebpageTool` for this migration
   - Other tools can be integrated or refactored in future iterations
5. Create `ToolRegistry` utility class:
   - Static method `List<Tool> getAllBedrockTools()` returns all registered Bedrock `Tool` objects
   - Static method `List<ToolImpl> getAllToolImplementations()` returns tool implementations for execution
   - Static method `ToolImpl findToolByName(String name)` looks up tool implementation by name
   - Note: For Bedrock integration, tool configuration is handled via AWS SDK `Tool` objects, not XML strings
   - Reference [3-tool-calling](../3-tool-calling/java/src/main/java/bitovi/activities/ActivitiesImpl.java) for tool configuration patterns

**Verification:**
- All tools implement the common interface
- `ToolRegistry.getAllTools()` returns BraveSearchTool and FetchWebpageTool
- `ToolRegistry.getToolsAsXmlString()` produces XML format for agent consumption
- Each tool's `execute()` method can be called independently
- Tool schemas compatible with AWS Bedrock tool configuration format

### Step 3: Add Configuration Properties

**Goal:** Ensure all required configuration is available before implementing utilities and activities.

**Actions:**
1. Add to `.env.example` file in workspace root:
   - `AWS_LOW_MODEL_ID` (low-quality Bedrock model for cost savings, e.g., anthropic.claude-3-haiku-20240307-v1:0)
   - `MAX_CONTEXT_TOKENS=12000`
   - Note: Other AWS properties (`AWS_REGION`, `AWS_MODEL_ID`, `AWS_ACCESS_KEY_ID`, etc.) and `BRAVE_SEARCH_API_KEY` should already exist
2. Config.java is already simple and loads from .env - keep it as is
3. No need for additional validation - simple property access is sufficient

**Verification:**
- Config loads all properties without errors
- Can access all properties via `Config.getProperty()`
- .env.example documents all required properties

### Step 4: Implement Token Estimation and Context Truncation

**Goal:** Implement simple token estimation and context truncation for managing conversation context in long-running agent conversations.

**Rationale:** AI models have context window limits. We need to estimate tokens to:
- Prevent hitting model context limits (which cause API errors)
- Trigger compaction at appropriate times
- Avoid over-aggressive truncation that loses conversation history

We'll use a simple character-based estimation (1 token ≈ 4 characters) which is sufficient for our needs.

**Actions:**
1. Create `ModelUtils` utility class in `bitovi.common`:
   - `int estimateTokenCount(String text)` - uses simple formula: `text.length() / 4`
   - `List<String> truncateContextToTokenLimit(List<String> context, int maxTokens)` 
   - Implement backwards traversal from most recent messages (keep the most recent entries)
   - Default max tokens from Config or 12000
2. Add `MAX_CONTEXT_TOKENS` property to Config (default: 12000)

**Verification:**
- `estimateTokenCount()` returns reasonable values for sample text
- `truncateContextToTokenLimit()` with context over limit returns subset from end
- `truncateContextToTokenLimit()` with context under limit returns unchanged list
- Config property loads from .env file

### Step 5: Update AWS.java for Agent Activities

**Goal:** Ensure AWS.java has appropriate methods for agent workflow activities.

**Actions:**
1. Review existing `bedrockConverse()` method in [3-tool-calling AWS.java](../3-tool-calling/java/src/main/java/bitovi/common/AWS.java)
2. Copy or adapt the method to `5-agent-workflow/java` AWS.java if not already present
3. Ensure method supports:
   - System prompts for agent instructions
   - Message history for conversation context
   - Both text responses and tool-calling responses
4. Add configuration support for high and low quality models:
   - `AWS_MODEL_ID` for high-quality thinking (thought activity)
   - `AWS_LOW_MODEL_ID` for cost-effective tasks (observation, compact activities)
5. Parse responses to extract text content and usage metadata

**Verification:**
- Method compiles and integrates with existing AWS class
- Can call with system prompt and get text response
- Usage metadata properly extracted from responses
- Configuration supports multiple model selection

### Step 6: Implement thoughtEntity Activity

**Goal:** Replace stub with full ReAct agent thinking implementation.

**Actions:**
1. Load `thought-prompt.txt` template from resources using `getClass().getResourceAsStream("/prompts/thought-prompt.txt")`
2. Get current date: `LocalDate.now().toString()`
3. Truncate context using `ModelUtils.truncateContextToTokenLimit(context, Config.MAX_CONTEXT_TOKENS)`
3. Get available tools list from ToolRegistry and format for prompt
5. Format prompt with placeholders replaced:
   - `{currentDate}` → current date
   - `{previousSteps}` → context joined with `\n`
   - `{availableActions}` → tools XML
6. Instruct the AI to respond in JSON format in the system prompt with structure (WITHOUT a `type` field):
   ```json
   {"thought": "...", "answer": "..."} OR {"thought": "...", "action": {"name": "...", "reason": "...", "input": {...}}}
   ```
7. Call `AWS.bedrockConverse()` with high-quality model (`AWS_MODEL_ID`)
8. Parse text response as JSON and determine type based on which fields are present:
   - If `answer` field exists → set `type = "answer"`
   - If `action` field exists → set `type = "action"`
   - Parse into `ThoughtResponse` record with field order: `type`, `thought`, `answer`, `action`, `usage`
   - Extract usage metadata from Bedrock response's `ConverseResponse.usage()` (we'll determine exact mapping during testing)
9. Return `ThoughtResponse`

**Verification:**
- Activity executes without errors on sample context
- Returns type="answer" when final response appropriate
- Returns type="action" with valid tool name when tool needed
- Usage metadata properly captured and returned
- Thought field always populated
- Prompt template loaded successfully from resources

### Step 7: Implement actionEntity Activity

**Goal:** Execute tool calls and return results.

**Actions:**
1. Get tool implementation by name: `ToolImpl tool = ToolRegistry.findToolByName(toolName)`
2. Handle tool not found case:
   - Return JSON error: `{"name": "toolName", "input": {...}, "error": "Tool not found"}`
3. Handle tool found case:
   - Convert `Object input` to `Map<String, Object>` (may need JSON parsing if String)
   - Call `tool.execute(inputMap)`
   - Wrap any exceptions and return as JSON error: `{"name": "toolName", "input": {...}, "error": "error message"}`
4. Return result string (either success or error JSON)
5. Log tool invocations for debugging

**Verification:**
- Calling with "brave_search" and valid input returns search results
- Calling with "fetch_webpage" and valid URL returns webpage content
- Calling with unknown tool name returns error JSON
- Calling with invalid input returns error JSON
- All executions logged

### Step 8: Implement observationEntity Activity

**Goal:** Generate observations from action results using AI.

**Actions:**
1. Load `observation-prompt.txt` template from resources using `getClass().getResourceAsStream("/prompts/observation-prompt.txt")`
2. Truncate context using `ModelUtils.truncateContextToTokenLimit()`
3. Format prompt:
   - `{previousSteps}` → truncated context joined with `\n`
   - `{actionResult}` → actionResult parameter
4. Call `AWS.bedrockConverse()` with low-quality model (`AWS_LOW_MODEL_ID`) for cost optimization
5. Extract text content from response
6. Extract usage metadata from `ConverseResponse.usage()` (exact field mapping to be determined during testing - reference [3-tool-calling AWS.java](../3-tool-calling/java/src/main/java/bitovi/common/AWS.java) for pattern)
7. Return `ObservationResponse` with observations text and usage

**Verification:**
- Activity returns meaningful observations from sample action results
- Observations relate to the action result content
- Usage metadata captured
- Uses low-quality model for cost savings

### Step 9: Implement compactEntity Activity

**Goal:** Compress context history when it grows too large.

**Actions:**
1. Load `compact-prompt.txt` template from resources using `getClass().getResourceAsStream("/prompts/compact-prompt.txt")`
2. Truncate input context using `ModelUtils.truncateContextToTokenLimit()`
3. Format prompt:
   - `{contextHistory}` → truncated context joined with `\n`
4. Call `AWS.bedrockConverse()` with low-quality model (`AWS_LOW_MODEL_ID`)
5. Extract compacted summary from response
6. Build result context: `[compactedSummary, ...lastNOriginalEntries]`
   - Take up to last 3 entries from original context (fewer if context is smaller)
   - Prepend the compacted summary
   - This preserves recent detail while summarizing older history
   - Edge case: if context has < 3 entries, take all available entries
   - Edge case: if compaction fails, keep at least the most recent entry
7. Extract usage metadata from `ConverseResponse.usage()`
8. Return `CompactResponse` with new context array and usage

**Verification:**
- Given context of 10+ entries, returns compacted version with 4 entries
- Most recent 3 entries preserved unchanged
- First entry is summary of older context
- Usage metadata captured
- Result is shorter than input but preserves key information

### Step 10: Update persistEntity Activity

**Goal:** Implement proper message persistence (for now, enhanced logging).

**Actions:**
1. Replace stub with proper formatting
2. For each message in list:
   - If role is "user": log `"{name} ({date}): {message}"`
   - If role is "assistant": log `"assistant: {message}"`
3. Add structured logging with workflow info if available
4. (Future enhancement: could add database persistence)

**Verification:**
- Messages logged with proper formatting
- User messages include name and date
- Assistant messages formatted correctly
- No errors when called with empty list

### Step 11: Integration Testing

**Goal:** Verify all activities work together in the workflow.

**Actions:**
1. Run "Exercise 5 Maven Build" task to compile
2. Start worker using debug configuration
3. Start client using debug configuration
4. Send test message requiring tool use
5. Observe workflow execution in Temporal UI
6. Verify:
   - ThoughtEntity returns action with valid tool name
   - ActionEntity executes tool and returns result
   - ObservationEntity generates meaningful observation
   - Process repeats until final answer
   - CompactEntity called when continue-as-new triggered
   - PersistEntity logs messages appropriately

**Verification:**
- Workflow completes successfully
- Final answer is relevant to input question
- All activities execute without errors
- Logs show proper progression through ReAct loop
- Usage metadata accumulated correctly
- Continue-as-new works for long conversations

### Step 12: Error Handling and Edge Cases

**Goal:** Ensure robust error handling throughout activities.

**Actions:**
1. Add try-catch blocks in all activities
2. Throw `ApplicationFailure` with clear error types:
   - "ThoughtEntityError"
   - "ActionEntityError"
   - "ObservationEntityError"
   - "CompactEntityError"
   - "PersistEntityError"
3. Add validation:
   - Non-null/non-empty context in thought/observation/compact
   - Valid tool names in action
   - Valid input formats
4. Add defensive null checks
5. Log all errors before throwing

**Verification:**
- Invalid inputs throw clear exceptions
- Null handling prevents NPEs
- All ApplicationFailures have meaningful error types and messages
- Errors logged before throwing
- Workflow can retry failed activities

---

## Implementation Notes

### Key Design Decisions

1. **Model Provider**: Using AWS Bedrock exclusively (no OpenAI dependencies)
   - High-quality model: `AWS_MODEL_ID` for thought generation
   - Low-quality model: `AWS_LOW_MODEL_ID` for observations and compaction (cost optimization)

2. **Token Estimation**: Simple character-based estimation (1 token ≈ 4 characters)
   - Sufficient for context window management
   - No external tokenization libraries needed

3. **Configuration**: Using `.env` file loaded by simple Config.java
   - No complex validation or type conversion
   - Properties accessed via `Config.getProperty(key)`

4. **Tool Schema Format**: AWS SDK `Document` type for Bedrock compatibility
   - Reference [3-tool-calling WeatherTool](../3-tool-calling/java/src/main/java/bitovi/activities/tools/WeatherTool.java)
   - Tools configured for Bedrock tool calling API

5. **Structured Output**: Instruct AI to respond in JSON format via system prompt
   - Parse JSON response into DTOs
   - No special Bedrock JSON mode required

6. **DTO Field Order**: Consistent with record definition
   - ThoughtResponse: `type`, `thought`, `answer`, `action`, `usage`
   - Use `type` field to determine response type ("answer" vs "action")

### Reference Implementations

- **AWS/Bedrock patterns**: [3-tool-calling/java/src/main/java/bitovi/common/AWS.java](../3-tool-calling/java/src/main/java/bitovi/common/AWS.java)
- **Tool definitions**: [3-tool-calling/java/src/main/java/bitovi/activities/tools/WeatherTool.java](../3-tool-calling/java/src/main/java/bitovi/activities/tools/WeatherTool.java)
- **Activity patterns**: [3-tool-calling/java/src/main/java/bitovi/activities/ActivitiesImpl.java](../3-tool-calling/java/src/main/java/bitovi/activities/ActivitiesImpl.java)
- **TypeScript reference**: [temp-ref-code/src/workflows/activities.ts](../temp-ref-code/src/workflows/activities.ts)
