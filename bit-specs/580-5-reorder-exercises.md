# Spec: Swap Exercise 6 and Exercise 7

## Overview

Reorder workshop exercises so that agent-decisions comes before agent-memory. This change improves the pedagogical flow by teaching decision-making patterns before the more complex memory architecture.

**Current State:**
- Exercise 6: agent-memory (long-term/short-term memory, STM architecture)
- Exercise 7: agent-decisions (routing, dynamic decision-making)

**Target State:**
- Exercise 6: agent-decisions (routing, dynamic decision-making)
- Exercise 7: agent-memory (long-term/short-term memory, STM architecture)

## Implementation Plan

### Step 1: Update VS Code Launch Configurations

**File:** [.vscode/launch.json](.vscode/launch.json)

**Changes Required:**

1. Update Exercise 6 launch configurations (currently agent-memory → should be agent-decisions):
   - Change `mainClass` from `bitovi.AgentMemoryWorker` to `bitovi.AgentDecisionsWorker`
   - Change `projectName` from `agent-memory` to `agent-decisions`
   - Update `cwd` from `${workspaceFolder}/6-agent-memory/java/` to `${workspaceFolder}/6-agent-decisions/java/`
   - Update `preLaunchTask` from `Exercise 6 Maven Build` to `Exercise 6 Maven Build` (will be updated in Step 2)

2. Update Exercise 6 Client configuration similarly

3. Move the current Exercise 6 utility launch configurations (List Events, Create Memory, Delete Memory, List Memory Records, Retrieve Memory Records) to Exercise 7

4. Update Exercise 7 launch configurations (currently agent-decisions → should be agent-memory):
   - Change `mainClass` from `bitovi.AgentDecisionsWorker` to `bitovi.AgentMemoryWorker`
   - Change `projectName` from `agent-decisions` to `agent-memory`
   - Update `cwd` from `${workspaceFolder}/7-agent-decisions/java/` to `${workspaceFolder}/7-agent-memory/java/`
   - Update `preLaunchTask` to `Exercise 7 Maven Build`

5. Update Exercise 7 Client configuration similarly

**Verification:**
- Launch configurations appear in VS Code Run and Debug dropdown
- Names and descriptions reflect new exercise numbers
- No duplicate or missing configurations

### Step 2: Update VS Code Build Tasks

**File:** [.vscode/tasks.json](.vscode/tasks.json)

**Changes Required:**

1. Update Exercise 6 Maven Build task:
   - Update `cwd` from `${workspaceFolder}/6-agent-memory/java` to `${workspaceFolder}/6-agent-decisions/java`

2. Update Exercise 7 Maven Build task:
   - Update `cwd` from `${workspaceFolder}/7-agent-decisions/java` to `${workspaceFolder}/7-agent-memory/java`

**Verification:**
- Run "Exercise 6 Maven Build" task and verify it builds from the agent-decisions directory
- Run "Exercise 7 Maven Build" task and verify it builds from the agent-memory directory
- Check task output paths match expected directories

### Step 3: Rename Directories

**Changes Required:**

Use temporary naming to avoid conflicts during the swap:
   ```
   6-agent-memory/ → temp-agent-memory/
   7-agent-decisions/ → 6-agent-decisions/
   temp-agent-memory/ → 7-agent-memory/
   ```

**Verification:**
- Directory `6-agent-decisions/` exists with agent-decisions content
- Directory `7-agent-memory/` exists with agent-memory content
- No `temp-` directories remain
- Both directories contain expected subdirectories (java/, typescript/, README.md)

### Step 4: Update Exercise Numbers in README Files

**Note:** README.md and TODO.md files will be manually updated after the directory swap is complete. This step can be skipped during implementation.

**Verification:**
- Directories have been successfully renamed
- Files are intact in new locations

### Step 5: Update Maven POM Files

**Files to Update:**
- [6-agent-decisions/java/pom.xml](6-agent-decisions/java/pom.xml)
- [7-agent-memory/java/pom.xml](7-agent-memory/java/pom.xml)

**Changes Required:**

1. In `6-agent-decisions/java/pom.xml`:
   - No changes needed (artifactId already correct: `agent-decisions`)

2. In `7-agent-memory/java/pom.xml`:
   - No changes needed (artifactId already correct: `agent-memory`)

**Verification:**
- Run `mvn compile` in both directories
- Verify builds succeed without errors
- Check that target/ directories are created correctly

### Step 6: Update Worker Print Statements

**Files to Update:**
- [6-agent-decisions/java/src/main/java/bitovi/AgentDecisionsWorker.java](6-agent-decisions/java/src/main/java/bitovi/AgentDecisionsWorker.java)
- [7-agent-memory/java/src/main/java/bitovi/AgentMemoryWorker.java](7-agent-memory/java/src/main/java/bitovi/AgentMemoryWorker.java)

**Changes Required:**

1. In `AgentDecisionsWorker.java`:
   - Change print statement from `"Exercise 7 Temporal Worker started. Press Ctrl+C to exit."` 
   - To: `"Exercise 6 Temporal Worker started. Press Ctrl+C to exit."`

2. In `AgentMemoryWorker.java`:
   - Change print statement from `"Exercise 6 Temporal Worker started. Press Ctrl+C to exit."`
   - To: `"Exercise 7 Temporal Worker started. Press Ctrl+C to exit."`

**Verification:**
- Start each worker using launch configurations
- Verify console output shows correct exercise number
- Verify workers connect to Temporal server successfully

### Step 7: Update Any Cross-References in Documentation

**Files to Check:**
- Main [README.md](README.md)
- [bit-specs/580-0-restructure-context-for-stm.md](bit-specs/580-0-restructure-context-for-stm.md)
- Any other spec files in `bit-specs/` directory

**Changes Required:**

1. Search for references to "Exercise 6" or "Exercise 7" in context of these exercises
2. Update file paths that reference `6-agent-memory` to `7-agent-memory`
3. Update file paths that reference `7-agent-decisions` to `6-agent-decisions`
4. Update any exercise numbers mentioned in narrative text

**Verification:**
- Grep search for "Exercise 6" and "Exercise 7" returns expected results
- Grep search for "6-agent-memory" and "7-agent-decisions" returns no results (except in this spec)
- All documentation links resolve correctly

### Step 8: Final Integration Test

**Test Procedure:**

1. Run Docker Compose to start Temporal server
2. Build both exercises using VS Code tasks
3. Launch Exercise 6 Worker (agent-decisions)
4. Launch Exercise 6 Client (agent-decisions)
5. Verify workflow executes correctly
6. Launch Exercise 7 Worker (agent-memory)
7. Launch Exercise 7 Client (agent-memory)
8. Verify workflow executes correctly
9. Test utility commands for Exercise 7 (List Memory Records, Create Memory, etc.)

**Success Criteria:**
- Both exercises build without errors
- Workers start with correct exercise numbers in output
- Clients can communicate with workers
- Workflows execute as expected
- No references to old exercise numbers in output
- All launch configurations work correctly
- All build tasks work correctly

## Additional Notes

Based on answers to implementation questions:
- TypeScript folders should be moved as-is with no file changes required
- No GitHub workflows, CI/CD pipelines, or automation reference these directories
- No presentation materials, slides, or instructor guides need updating
- No git branch names or tags need updating

## Review Findings

### Verification Against Codebase

✅ **Correct in spec:**
- Launch.json configurations match current state
- Tasks.json configurations match current state  
- Worker print statements match (Exercise 6 in AgentMemoryWorker, Exercise 7 in AgentDecisionsWorker)
- POM artifactIds are already correct (agent-memory and agent-decisions)
- TypeScript folders exist in both exercises
- Utility classes (CreateMemory, DeleteMemory, etc.) exist only in 6-agent-memory as expected

✅ **No issues found:**
- No TypeScript package.json files reference exercise numbers
- Main README doesn't reference specific exercise numbers for these exercises
- Launch configuration names follow consistent pattern

### Implementation Notes

- README.md and TODO.md files will be manually updated after implementation
- No GitHub workflows, CI/CD pipelines, or automation reference these directories
- No presentation materials, slides, or instructor guides need updating
- No git branch names or tags need updating
