# SYSTEMS-580-1: Migrate Worker Implementation

This is a subsection of [580-0-convert-ts-to-java.md](./580-0-convert-ts-to-java.md)

## Overview

Migrate the worker from [temp-ref-code/src/worker.ts](../temp-ref-code/src/worker.ts) into the [5-agent-workflow/java](../5-agent-workflow/java/) implementation. The worker is already partially implemented but needs to be validated and aligned with the TypeScript reference implementation.

## Context

The TypeScript worker implementation in [temp-ref-code/src/worker.ts](../temp-ref-code/src/worker.ts) creates a Temporal worker that:
- Connects to the Temporal server using configuration from environment variables
- Registers workflow and activity implementations
- Starts listening on a task queue
- Handles graceful shutdown on SIGINT/SIGTERM signals

The Java worker at [5-agent-workflow/java/src/main/java/bitovi/AgentWorkflowWorker.java](../5-agent-workflow/java/src/main/java/bitovi/AgentWorkflowWorker.java) already exists and follows the same pattern as other exercises (Exercises 0-4), which is good. However, we need to verify it's correctly configured and follows best practices from the existing examples.

## Current State

### What Exists
- [AgentWorkflowWorker.java](../5-agent-workflow/java/src/main/java/bitovi/AgentWorkflowWorker.java) - Worker main class ✅
- [AgentWorkflow.java](../5-agent-workflow/java/src/main/java/bitovi/AgentWorkflow.java) - Workflow interface ✅
- [AgentWorkflowImpl.java](../5-agent-workflow/java/src/main/java/bitovi/AgentWorkflowImpl.java) - Workflow implementation (stubbed) ⚠️
- [Activities.java](../5-agent-workflow/java/src/main/java/bitovi/activities/Activities.java) - Activities interface ✅
- [ActivitiesImpl.java](../5-agent-workflow/java/src/main/java/bitovi/activities/ActivitiesImpl.java) - Activities implementation (stubbed) ⚠️
- [AgentWorkflowClient.java](../5-agent-workflow/java/src/main/java/bitovi/AgentWorkflowClient.java) - Client for starting workflows ✅
- [TemporalClient.java](../5-agent-workflow/java/src/main/java/bitovi/common/TemporalClient.java) - Temporal client factory ✅
- [Config.java](../5-agent-workflow/java/src/main/java/bitovi/common/Config.java) - Configuration loader ✅
- VS Code launch configuration for "Exercise 5 - Worker" ✅
- VS Code launch configuration for "Exercise 5 - Client" ✅
- Maven build task for Exercise 5 ✅

### What Needs Implementation
The worker itself is complete, but we need to:
1. Verify the worker structure matches the reference implementation pattern
2. Ensure proper error handling and logging
3. Confirm graceful shutdown behavior
4. Validate integration with the existing infrastructure

## Implementation Plan

### Step 1: Verify Worker Configuration
**Goal:** Ensure the worker is correctly configured to match TypeScript reference implementation

**Actions:**
- Review [AgentWorkflowWorker.java](../5-agent-workflow/java/src/main/java/bitovi/AgentWorkflowWorker.java) structure
- Compare with TypeScript [worker.ts](../temp-ref-code/src/worker.ts) configuration
- Verify it follows the same pattern as [ToolCallingWorker.java](../3-tool-calling/java/src/main/java/bitovi/ToolCallingWorker.java) and [RagWorker.java](../2-rag/java/src/main/java/bitovi/RagWorker.java)
- Check configuration loading (task queue, namespace, connection settings)

**How to verify:**
- Worker class structure matches other exercise patterns
- Config properties are loaded correctly from `.env` file
- WorkerFactory and Worker are initialized properly
- Workflow and activity implementations are registered

### Step 2: Enhance Error Handling and Logging
**Goal:** Improve error messages and logging to match existing Java implementations

**Actions:**
- Review error handling in the main method
- Add logging consistent with other exercises (0-4) - use System.out.println for standard messages
- Ensure error messages are descriptive and actionable
- Add logging for successful worker startup with configuration details
- **Note**: Use existing Java implementations (ToolCallingWorker, RagWorker, etc.) as the reference for logging patterns, not the TypeScript implementation

**How to verify:**
- Worker logs match the pattern from other Java exercises
- Worker logs task queue configuration on startup
- Error messages include stack traces and contextual information
- Console output follows Java conventions: "Exercise 5 Temporal Worker started. Press Ctrl+C to exit."

### Step 3: Validate Thread Management
**Goal:** Ensure the worker runs indefinitely and handles shutdown correctly

**Actions:**
- Verify `Thread.currentThread().join()` keeps worker alive
- Confirm the try-catch block properly handles exceptions
- Check exit codes match other Java workers (exit 1 on error)
- Verify no runtime configuration change support is needed (restart required for config updates)
- Note: Java doesn't have direct equivalents to Node.js SIGINT/SIGTERM handlers, but the Temporal Java SDK handles this internally

**How to verify:**
- Worker continues running after startup
- Worker can be stopped with Ctrl+C from VS Code debug console
- Worker terminates on unhandled exceptions with exit code 1
- Factory.start() is called before the join statement
- No metrics, health checks, or monitoring hooks needed beyond default Temporal SDK behavior

### Step 4: Test Worker with Stub Workflow
**Goal:** Verify the worker can be started and stopped successfully

**Actions:**
- Use the VS Code launch configuration "Exercise 5 - Worker"
- Run the worker using the debug configuration
- Verify it connects to Temporal server
- Check Temporal Web UI at http://localhost:8233 to confirm worker is polling
- Test stopping the worker gracefully

**How to verify:**
- Worker starts without errors
- Console shows "Exercise 5 Temporal Worker started. Press Ctrl+C to exit."
- Worker appears in Temporal Web UI as polling the correct task queue
- Worker stops cleanly when terminated

### Step 5: Test Client Integration with Stub Workflow
**Goal:** Verify the client can start workflows that the worker executes

**Actions:**
- Start the worker using "Exercise 5 - Worker" launch config
- Run the client using "Exercise 5 - Client" launch config
- Verify workflow is created and executed
- Check the workflow returns "Success" (the current stub response)
- Review workflow execution in Temporal Web UI

**How to verify:**
- Client creates a workflow with ID format: `agent-workflow-{uuid}-{userId}`
- Worker picks up and executes the workflow
- Client receives response: "Workflow Executed: Success"
- Workflow appears in Temporal Web UI with status "Completed"
- No exceptions or errors in either worker or client logs

### Step 6: Compare with Reference Implementation
**Goal:** Ensure structural alignment with TypeScript worker

**Actions:**
- Verify the TypeScript reference implementation ([workflow.ts](../temp-ref-code/src/workflows/workflow.ts)) has only one workflow: `agentEntityWorkflow`
- Confirm AgentWorkflowWorker.java registers only `AgentWorkflowImpl.class`
- Compare with Exercise 2 (RagWorker) which registers multiple workflows - note this is intentionally different
- Ensure no additional workflow types are registered

**How to verify:**
- Only `worker.registerWorkflowImplementationTypes(AgentWorkflowImpl.class)` is called
- No other workflow types are registered
- Pattern matches single-workflow exercises (0, 1, 3, 4) rather than multi-workflow Exercise 2
- All TypeScript worker features have Java equivalents
- No missing configuration or capabilities

### Step 7: Validate Concurrency Configuration
**Goal:** Ensure worker handles concurrent workflow executions appropriately

**Actions:**
- Review how other Java exercises handle concurrency (no explicit limits by default)
- Confirm the worker uses default Temporal SDK concurrency settings
- Verify no explicit `WorkerOptions` are needed for concurrency control
- Note: The Temporal Java SDK handles concurrent workflow execution automatically

**How to verify:**
- No custom concurrency configuration is set
- Worker can handle multiple workflow executions as per Temporal SDK defaults
- Pattern matches other exercises (0-4) for concurrency handling

### Step 8: Document Worker Configuration
**Goal:** Update README with worker setup and usage information

**Actions:**
- Update [5-agent-workflow/java/README.md](../5-agent-workflow/java/README.md) with:
  - Worker purpose and architecture
  - How to start the worker (VS Code launch config)
  - Expected console output
  - How to verify worker is running
  - Link to Temporal Web UI
  - Graceful shutdown instructions (requires restart for config changes)

**How to verify:**
- README is comprehensive and matches pattern from other exercises
- New users can follow the README to start and verify the worker
- Includes troubleshooting section for common issues
- Documented graceful shutdown process

## Configuration Requirements

Based on answers to requirements questions:

1. **Logging**: Use the same logging approach as other Java exercises (0-4). Use `System.out.println` for standard output. Do not follow TypeScript logging patterns if they differ from existing Java implementations.

2. **Configuration Changes**: No runtime configuration change support needed. Configuration updates require worker restart.

3. **Metrics & Monitoring**: No custom metrics, health checks, or monitoring required beyond default Temporal SDK behavior.

4. **Workflow Types**: Worker registers only `AgentWorkflow` (single workflow type). The TypeScript reference has one workflow (`agentEntityWorkflow`). This differs from Exercise 2 which has multiple workflow types.

5. **Concurrency**: Use default Temporal SDK concurrency settings. Follow the pattern from other Java exercises (0-4) - no explicit concurrency limits configured.

## Notes

- The worker implementation itself appears to be already complete and follows the established patterns from exercises 0-4
- The main work is validation and documentation rather than new code
- The worker depends on ActivitiesImpl and AgentWorkflowImpl which are still stubbed - this is expected and will be implemented in subsequent tasks
- Java's Temporal SDK handles worker lifecycle and shutdown internally, unlike Node.js which requires explicit signal handlers
- Follow existing Java exercise patterns for all implementation details, using TypeScript only as functional referenceing java implementations. If the typescript logging is significantly different, prefer the existing java implementations as a reference.

2. Should the worker support any runtime configuration changes, or is it acceptable to require a restart for configuration updates?
- no runtime configuration changes, restart is acceptable

3. Are there any specific metrics or monitoring requirements for the worker (e.g., worker health checks, activity execution counts)?
- no

4. Should the worker support multiple workflow types, or is it dedicated to AgentWorkflow only? (Note: Looking at Exercise 2, RagWorker registers multiple workflow types)
- I think the typescript reference code only has one workflow. If I'm wrong correct my.

5. Is there a requirement for the worker to handle multiple concurrent workflow executions, and if so, should there be any concurrency limits configured?
- take reference from the existing java implementations for this