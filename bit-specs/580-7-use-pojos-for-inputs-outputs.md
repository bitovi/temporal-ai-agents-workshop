# Use POJOs for Inputs/Outputs in Exercise 5

## Problem Statement

Currently, the activities and workflow in `5-agent-workflow` are experiencing serialization issues where inputs/outputs contain Java-specific Map metadata (e.g., "mapType") instead of clean JSON. This happens because:

1. `ActionDetail` record uses `Object input` field
2. `Activities.actionActivity()` accepts `Object input` parameter
3. When Temporal serializes these `Object` types, it includes Java-specific type information

## Goal

Replace `Object` types with proper Java records (POJOs) to ensure clean JSON serialization across all activities and the workflow. Prioritize records for immutability and simplicity.

## Implementation Plan

### Step 1: Identify All Activity Input/Output Types

**Action:**
- Review all activity methods in [Activities.java](5-agent-workflow/java/src/main/java/bitovi/activities/Activities.java)
- Document current parameter types and return types
- Identify which use `Object` or raw types

**Current State:**
```java
ThoughtResponse thoughtActivity(List<String> context)
String actionActivity(String toolName, Object input) // ❌ Object input
ObservationResponse observationActivity(List<String> context, String actionResult)
CompactResponse compactActivity(List<String> context)
void persistActivity(List<PersistMessage> messages)
```

**Expected Output:**
- List of all activities and their current signatures
- Identification of problematic `Object` types

**Verification:**
- All activity methods documented
- All `Object` types identified

---

### Step 2: Create ActionInput Record

**Action:**
- Create a new record `ActionInput` in `bitovi.activities.types` package
- This record should represent the structured input for tool execution
- Use `Map<String, Object>` for the tool-specific parameters field

**Implementation:**

Create [ActionInput.java](5-agent-workflow/java/src/main/java/bitovi/activities/types/ActionInput.java):
```java
package bitovi.activities.types;

import java.util.Map;

/**
 * Input parameter wrapper for tool action execution.
 * Ensures clean JSON serialization by explicitly typing the parameters map.
 */
public record ActionInput(
    Map<String, Object> parameters
) {
}
```

**Rationale:**
- Using `Map<String, Object>` for parameters allows flexibility for different tools (keeping generic approach as it provides flexibility for dynamic tools)
- Temporal can properly serialize Map types when they're explicitly typed
- The record wrapper ensures type safety at the activity boundary
- Null checking will be handled in the activity implementation

**Verification:**
- File compiles without errors
- Record can be instantiated with a Map

---

### Step 3: Update ActionDetail to Use ActionInput

**Action:**
- Modify `ActionDetail` record to replace `Object input` with `ActionInput input`
- This ensures type safety from the thought activity through to action activity

**Implementation:**

Update [ActionDetail.java](5-agent-workflow/java/src/main/java/bitovi/activities/types/ActionDetail.java):
```java
package bitovi.activities.types;

public record ActionDetail(
    String name,
    String reason,
    ActionInput input  // Changed from Object
) {
}
```

**Verification:**
- File compiles without errors
- Run `mvn compile` in `5-agent-workflow/java` directory

---

### Step 4: Update Activities Interface

**Action:**
- Update `actionActivity` signature to use `ActionInput` instead of `Object`
- This creates a type-safe contract

**Implementation:**

Update [Activities.java](5-agent-workflow/java/src/main/java/bitovi/activities/Activities.java):
```java
@ActivityMethod
String actionActivity(String toolName, ActionInput input) throws ApplicationFailure;
```

**Verification:**
- Interface compiles
- Note: Implementation will need updating (next step)

---

### Step 5: Update ActivitiesImpl.actionActivity()

**Action:**
- Modify the implementation to work with `ActionInput` type
- Extract the parameters map from the ActionInput record
- Remove Object type handling logic (instanceof checks for String, Map, JSONObject)

**Implementation:**

In [ActivitiesImpl.java](5-agent-workflow/java/src/main/java/bitovi/activities/ActivitiesImpl.java), update `actionActivity`:

```java
@Override
public String actionActivity(String toolName, ActionInput input) throws ApplicationFailure {
    try {
        System.out.println("actionActivity called with tool: " + toolName);
        
        // Check if tool exists
        if (!ToolRegistry.hasToolNamed(toolName)) {
            EventClient.emitEvent("error", "Tool with name " + toolName + " not found.");
            JSONObject errorResult = new JSONObject();
            errorResult.put("name", toolName);
            errorResult.put("input", input.parameters());
            errorResult.put("error", "Tool not found");
            return errorResult.toString();
        }
        
        // Get parameters from ActionInput
        Map<String, Object> inputMap = input.parameters();
        
        // Execute tool
        try {
            EventClient.emitEvent("action", "Invoked tool " + toolName + 
                " with input " + new JSONObject(inputMap).toString());
            
            String result = ToolRegistry.executeTool(toolName, inputMap);
            System.out.println("Tool execution successful: " + toolName);
            return result;
        } catch (Exception e) {
            String errorMsg = "Error executing tool " + toolName + ": " + e.getMessage();
            System.err.println(errorMsg);
            EventClient.emitEvent("error", errorMsg);
            JSONObject errorResult = new JSONObject();
            errorResult.put("name", toolName);
            errorResult.put("input", inputMap);  // Use Map for clean JSON serialization
            errorResult.put("error", e.getMessage());
            return errorResult.toString();
        }
        
    } catch (Exception e) {
        System.err.println("Error in actionActivity: " + e.getMessage());
        throw ApplicationFailure.newFailure("actionActivity failed: " + e.getMessage(), 
                "ActionActivityError");
    }
}
```

**Changes:**
- Remove all `instanceof` checks for different Object types
- Directly access `input.parameters()` as a Map
- Simplify error handling to use the Map directly

**Verification:**
- Code compiles
- No type conversion errors

---

### Step 6: Update ActivitiesImpl.thoughtActivity() JSON Parsing

**Action:**
- When parsing the action from the model response, create an `ActionInput` instance
- Convert the JSON input field to a Map and wrap it in ActionInput

**Implementation:**

In [ActivitiesImpl.java](5-agent-workflow/java/src/main/java/bitovi/activities/ActivitiesImpl.java), update the action parsing section in `thoughtActivity`:

```java
} else if (jsonResponse.has("action")) {
    type = "action";
    JSONObject actionObj = jsonResponse.getJSONObject("action");
    String name = actionObj.getString("name");
    String reason = actionObj.optString("reason", "");
    
    // Parse input as Map and wrap in ActionInput
    Object inputObj = actionObj.get("input");
    Map<String, Object> inputMap;
    if (inputObj instanceof JSONObject) {
        inputMap = ((JSONObject) inputObj).toMap();
    } else if (inputObj instanceof Map) {
        inputMap = (Map<String, Object>) inputObj;
    } else {
        // Fallback for unexpected input types
        System.out.println("Warning: Unexpected input type " + inputObj.getClass().getName() + 
            ", wrapping in 'value' key");
        inputMap = new HashMap<>();
        inputMap.put("value", inputObj);
    }
    
    // Validate non-null before creating ActionInput
    if (inputMap == null) {
        inputMap = new HashMap<>();
    }
    
    ActionInput actionInput = new ActionInput(inputMap);
    action = new ActionDetail(name, reason, actionInput);
    
    // Emit events for action type
    EventClient.emitEvent("thought", thought);
}
```

**Note:**
- Add `import java.util.HashMap;` at the top of the file if not already present
- The warning log helps identify unexpected input formats during development
- Null check ensures ActionInput never receives null parameters

**Verification:**
- Compiles without errors
- Handles various JSON input formats gracefully
- Warning is logged for unexpected input types

---

### Step 7: Update AgentWorkflowImpl Activity Call

**Action:**
- Update the workflow implementation to pass ActionInput correctly
- No changes should be needed since ActionDetail now contains ActionInput

**Implementation:**

In [AgentWorkflowImpl.java](5-agent-workflow/java/src/main/java/bitovi/workflow/AgentWorkflowImpl.java), verify the call at line ~193:

```java
String actionResult = activities.actionActivity(action.name(), action.input());
```

This should now pass `ActionInput` type correctly since `action.input()` returns `ActionInput`.

**Verification:**
- Workflow compiles
- Type checking passes

---

### Step 8: Update Workflow Action Serialization for Context

**Action:**
- Update how actions are serialized to context strings in the workflow
- Access the parameters map from ActionInput when creating context string

**Implementation:**

In [AgentWorkflowImpl.java](5-agent-workflow/java/src/main/java/bitovi/workflow/AgentWorkflowImpl.java), update the action context creation (around line 180):

```java
// Serialize action input for context
String actionInputJson;
try {
    actionInputJson = new JSONObject(action.input().parameters()).toString();
} catch (Exception e) {
    actionInputJson = "{}";
}

// Add action to context
String actionContext = String.format(
    "<action><reason>\n%s\n</reason><name>%s</name><input>%s</input></action>",
    action.reason(), action.name(), actionInputJson);
context.add(actionContext);
```

**Changes:**
- Access `action.input().parameters()` instead of `action.input()`
- Provide fallback empty object instead of String.valueOf

**Note:**
- `JSONObject` is already imported in AgentWorkflowImpl.java (from org.json)

**Verification:**
- Compiles successfully
- Context strings should now contain clean JSON

---

### Step 9: Review Other Activity Types

**Action:**
- Review remaining activity types to ensure they don't have similar issues
- Verify all types are using proper records or POJOs

**Current Types to Review:**
- ✅ `ThoughtResponse` - already a record
- ✅ `ObservationResponse` - already a record  
- ✅ `CompactResponse` - already a record
- ✅ `PersistMessage` - confirmed as a record
- ✅ `WorkflowInput` - already a record
- ✅ `WorkflowResult` - already a record
- ✅ `MessagePayload` - already a record
- ✅ `ContinueAsNewState` - already a record
- ✅ `UsageMetadata` - already a record

**Verification:**
- All types are records or proper POJOs
- No remaining `Object` parameters in activity methods besides the ones being fixed

---

### Step 10: Build and Test Compilation

**Action:**
- Run Maven compile to ensure all changes work together
- Fix any compilation errors that arise

**Implementation:**

```bash
cd /Users/michael/code/bitovi/temporal-ai-agents-workshop/5-agent-workflow/java
mvn clean compile
```

**Verification:**
- Build succeeds with no errors
- All classes compile
- No type mismatch errors

---

### Step 11: Test Runtime Serialization

**Action:**
- Start the workflow worker
- Send a test message that triggers the agent workflow
- Verify that serialized data no longer contains "mapType" or other Java-specific metadata

**Implementation:**

1. Start worker: Run the debug configuration or execute:
   ```bash
   mvn exec:java -Dexec.mainClass="bitovi.AgentWorkflowWorker"
   ```

2. Test the workflow with a sample query

3. Inspect Temporal UI or logs to verify serialization

**Expected Behavior:**
- Action inputs should serialize as clean JSON: `{"parameters": {"query": "test"}}`
- No "mapType", "hashType", or other Java metadata
- Workflow history shows clean JSON payloads

**Verification:**
- Check Temporal UI workflow history
- Review activity inputs in the history
- Confirm JSON is clean and readable

---

### Step 12: Documentation and Cleanup

**Action:**
- Update any comments that reference the old Object types
- Delete unused ActionResponse class (confirmed to have no usages in codebase)

**Implementation:**

1. Delete [ActionResponse.java](5-agent-workflow/java/src/main/java/bitovi/activities/types/ActionResponse.java):
   - File exists but has no usages in the codebase
   - Was likely created for a previous design but never integrated

2. Review code for any remaining comments referencing `Object` types in activity parameters

**Verification:**
- ActionResponse.java file is deleted
- No references to ActionResponse remain in the codebase
- No comments reference old Object parameter types
- All documentation is up to date

---

## Notes

- Focus is on Exercise 5 only; other exercises do not require similar changes
- No backward compatibility handling needed (development/testing only)
- Generic `Map<String, Object>` approach retained for tool parameter flexibility
- Null validation handled in activity code, not in ActionInput record
- ActionResponse.java confirmed as unused and will be deleted
- Steps reordered to review types before building