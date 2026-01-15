# SYSTEMS-580-2: Migrate Agent Chat Server to Dockerized Structure

This is a subsection of [580-0-convert-ts-to-java.md](./580-0-convert-ts-to-java.md)

## Overview

Migrate the Express-based agent chat server from `temp-ref-code/src/server.ts` into a standalone, Dockerized TypeScript application. This server provides a REST API and Server-Sent Events (SSE) interface for managing conversational workflows with Temporal.

The server:
- Manages conversation lifecycle via REST endpoints
- Integrates with Temporal workflows to start and signal agent entities
- Provides SSE for real-time event streaming from worker activities
- Serves a static HTML frontend for testing

## Implementation Plan

### Step 1: Create the Agent Chat Server Directory Structure

Create a new directory `agent-chat-server/` at the repository root with the following structure:

```
agent-chat-server/
├── Dockerfile
├── package.json
├── tsconfig.json
├── .dockerignore
├── src/
│   └── server.ts
└── public/
    └── index.html
```

**Verification:**
- Directory structure matches above layout
- All directories created at correct location

### Step 2: Copy and Configure Source Files

Copy the following files from `temp-ref-code/`:
- `src/server.ts` → `agent-chat-server/src/server.ts`
- `public/index.html` → `agent-chat-server/public/index.html`

**Verification:**
- Files copied successfully
- No import path errors (will fix in next step)

### Step 3: Create package.json

Create `agent-chat-server/package.json` with dependencies needed for the Express/TypeScript server:

Key dependencies:
- `express` (^5.2.1) - Web server framework
- `@temporalio/client` (^1.13.2) - Temporal client for workflow interaction
- `dotenv` (^17.2.3) - Environment configuration

DevDependencies:
- `@types/express` (^5.0.6) - TypeScript types for Express
- `@types/node` (^24.7.0) - TypeScript types for Node.js
- `@tsconfig/node24` (^24.1.3) - Base TypeScript config for Node.js 24
- `ts-node` (^10.9.2) - TypeScript execution for development
- `typescript` (^5.7.3) - TypeScript compiler

Scripts (critical for Docker):
- `start`: `node lib/server.js` - Run the compiled server in production mode
- `dev`: `ts-node src/server.ts` - Development mode with hot reload
- `build`: `tsc` - Compile TypeScript to JavaScript

**Verification:**
- Valid JSON syntax
- All required dependencies present
- Engine specifies Node.js 24

### Step 4: Create tsconfig.json

Create `agent-chat-server/tsconfig.json` configured for:
- Node.js 24 targeting (extend from `@tsconfig/node24`)
- Module system: `nodenext` with `moduleResolution: nodenext`
- Output directory: `./lib`
- Source directory: `./src`
- Strict mode enabled
- Source maps enabled for debugging

**Verification:**
- Valid JSON configuration
- Extends `@tsconfig/node24/tsconfig.json`
- Includes/excludes configured correctly

### Step 5: Update server.ts Import Paths

Modify `agent-chat-server/src/server.ts` to fix imports since we're extracting from the larger codebase:

1. Remove references to `./internals/config` - replace with direct environment variable access via `process.env`
2. Remove references to workflow imports from `./workflows/workflow` - hardcode the signal/workflow names as strings since they're just used as identifiers
3. Update the Temporal connection configuration to use environment variables directly (matching Java implementation naming):
   - `TEMPORAL_HOST_PORT` (default: `localhost:7233`)
   - `TEMPORAL_NAMESPACE` (default: `default`)
   - `TEMPORAL_TASK_QUEUE` (default: `agent-queue`)

Changes needed:
```typescript
// Replace Config.TEMPORAL_CLIENT_OPTIONS with:
const connection = await Connection.connect({
  address: process.env.TEMPORAL_HOST_PORT || 'localhost:7233'
});

// Replace Config.TEMPORAL_NAMESPACE with:
const namespace = process.env.TEMPORAL_NAMESPACE || 'default';

// Replace Config.TEMPORAL_TASK_QUEUE with:
const taskQueue = process.env.TEMPORAL_TASK_QUEUE || 'agent-queue';

// Replace workflow imports with string constants
// These names match the temp-ref-code TypeScript implementation
// Note: Java signal handlers will be implemented in future exercises
const WORKFLOW_NAME = 'agentWorkflow';
const MESSAGE_SIGNAL = 'agentWorkflowMessage';
const EXIT_SIGNAL = 'agentWorkflowExit';
```

**Verification:**
- No import errors
- `tsc --noEmit` passes without errors
- Server can be instantiated (next step)

### Step 6: Create Dockerfile

Create `agent-chat-server/Dockerfile` using the Node.js Alpine pattern from `mock-mcp-server-ts/Dockerfile`, with TypeScript compilation added:

```dockerfile
FROM node:24-alpine

WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

COPY package*.json ./
RUN npm install

COPY . .
RUN npm run build

EXPOSE 3000

CMD ["npm", "start"]
```

Key additions for TypeScript:
- Include build step (`npm run build`) to compile TypeScript
- Expose port 3000 (standard Express port)
- Use built output (lib/server.js) for production via `npm start`

**Verification:**
- Dockerfile builds successfully: `docker build -t agent-chat-server ./agent-chat-server`
- Image size is reasonable (< 500MB)

### Step 7: Create .dockerignore

Create `agent-chat-server/.dockerignore` to exclude unnecessary files:

```
node_modules
lib
npm-debug.log
.env
.git
```

**Verification:**
- Build time improves after adding .dockerignore
- Build context size is minimal

### Step 8: Update docker-compose.yml

Add the agent-chat-server service to the root `docker-compose.yml` and ensure temporal-dev-server is on the same network:

```yaml
agent-chat-server:
  container_name: agent-chat-server
  build:
    context: ./agent-chat-server
    dockerfile: Dockerfile
  ports:
    - 3000:3000
  environment:
    - TEMPORAL_HOST_PORT=temporal-dev-server:7233
    - TEMPORAL_NAMESPACE=default
    - TEMPORAL_TASK_QUEUE=agent-queue
    - PORT=3000
  networks:
    - docker-network
  depends_on:
    temporal-dev-server:
      condition: service_healthy
  healthcheck:
    test: ["CMD-SHELL", "curl -f http://localhost:3000/ || exit 1"]
    interval: 30s
    timeout: 10s
    retries: 3
```

Also update the `temporal-dev-server` service to add it to docker-network while keeping it accessible from host:

```yaml
temporal-dev-server:
  container_name: temporal-dev-server
  image: temporalio/auto-setup:latest
  ports:
    - "7233:7233"  # Temporal gRPC endpoint (host access for local dev)
    - "8233:8233"  # Temporal Web UI
  environment:
    - DB=sqlite
    - SQLITE_PRAGMA_journal_mode=WAL
  volumes:
    - ./temporal-data:/etc/temporalite
  networks:
    - docker-network  # Add for container-to-container communication
  healthcheck:
    test: ["CMD", "tctl", "cluster", "health"]
    interval: 1s
    timeout: 5s
    retries: 30
```

Also ensure the `docker-network` is defined at the bottom of docker-compose.yml:

```yaml
networks:
  docker-network:
    driver: bridge
```

Key configuration:
- Connect to `temporal-dev-server:7233` (internal Docker network address) using `TEMPORAL_HOST_PORT` (matching Java implementation)
- Map port 3000 to host for browser access
- Both services on `docker-network` for container-to-container communication
- temporal-dev-server also exposes port to host for development
- Depend on Temporal being healthy
- Healthcheck uses wget (pre-installed in Alpine)

**Verification:**
- `docker compose config` validates syntax
- Service name is unique in the compose file

### Step 9: Update package.json start script

Ensure `agent-chat-server/package.json` has the correct start script for production:

```json
{
  "scripts": {
    "start": "node lib/server.js",
    "dev": "ts-node src/server.ts",
    "build": "tsc"
  }
}
```

**Verification:**
- Build produces `lib/server.js`
- Start script references the correct output file

### Step 10: Test Docker Build

Build the Docker image independently:



```bash
docker build -t agent-chat-server ./agent-chat-server
```

**Verification:**
- Build completes without errors
- All TypeScript files compile
- Dependencies install correctly
- Final image created

### Step 11: Test with Docker Compose

Start all services with Docker Compose:

```bash
docker compose up --build agent-chat-server
```

**Verification:**
- agent-chat-server container starts
- Healthcheck passes
- Logs show "Temporal client initialized"
- Logs show "Server running on port 3000"
- No connection errors to Temporal

### Step 12: Verify End-to-End Functionality

Test the server is working correctly:

1. **Access the UI:**
   - Open browser to `http://localhost:3000`
   - Should see "Agent Workflow" interface
   - Status should show "✓ Connected to event stream"

2. **Test REST endpoints:**
   ```bash
   # Create conversation
   curl -X POST http://localhost:3000/api/conversations
   
   # List conversations
   curl http://localhost:3000/api/conversations
   ```

3. **Test SSE stream:**
   ```bash
   curl -N http://localhost:3000/events
   ```
   Should receive connected event

4. **Test full workflow** (requires worker running):
   - Create new conversation via UI
   - Send a message
   - Verify events stream in the events panel
   - Exit conversation

**Verification:**
- All REST endpoints respond correctly
- SSE connection establishes
- UI loads and is interactive
- Server can connect to Temporal (if temporal-dev-server is running)

### Step 13: Add Environment Configuration Documentation

Create `agent-chat-server/README.md` documenting:

**Environment Variables:**
- `TEMPORAL_HOST_PORT` - Temporal server address (default: localhost:7233)
- `TEMPORAL_NAMESPACE` - Temporal namespace (default: default)
- `TEMPORAL_TASK_QUEUE` - Task queue name (default: agent-queue)
- `PORT` - Server port (default: 3000)

**Local Development:**
```bash
npm install
npm run dev
```

Note: The server connects to Temporal via the Docker Compose setup. For local development, ensure `temporal-dev-server` is running:
```bash
# From repository root
docker compose up temporal-dev-server
```

Then set `TEMPORAL_HOST_PORT=localhost:7233` to connect to the dockerized Temporal server from your local dev environment.

**Docker Build:**
```bash
docker build -t agent-chat-server .
docker run -p 3000:3000 -e TEMPORAL_HOST_PORT=temporal-dev-server:7233 agent-chat-server
```

**Docker Compose (Recommended):**
```bash
# From repository root
docker compose up agent-chat-server
```

**Verification:**
- README exists and is clear
- Instructions can be followed successfully

### Step 14: Clean Up References

Remove or archive `temp-ref-code/` if no longer needed:
- Verify all necessary code has been migrated
- Consider keeping as reference but document in main README that it's archived

**Verification:**
- agent-chat-server is fully independent
- No broken references in documentation
- Repository is clean

## Success Criteria

- ✅ agent-chat-server directory created with proper structure
- ✅ Server builds successfully with TypeScript
- ✅ Docker image builds without errors
- ✅ Service starts via docker-compose
- ✅ Healthcheck passes
- ✅ Server is accessible from host machine on port 3000
- ✅ UI loads and displays correctly
- ✅ SSE connection works
- ✅ REST API endpoints respond
- ✅ Server can connect to Temporal
- ✅ Documentation is complete

## Implementation Notes

### Workflow Compatibility
This server uses workflow and signal names from the `temp-ref-code` TypeScript implementation:
- `agentWorkflow`
- `agentWorkflowMessage`
- `agentWorkflowExit`

The Java worker signal handlers will be implemented in future exercises. For now, the server is compatible with the TypeScript worker from temp-ref-code.

### Environment Configuration
Environment variables can be managed via:
- Docker Compose (recommended for containerized deployment)
- `.env` file (for local development - create manually as needed)
- Direct environment variable export

