# Dockerize Book Agent Server

## Overview

Containerize the `/book-agent-server` application to run in Docker, enabling deployment in the docker-compose environment alongside other workshop services. The book agent server is an A2A (Agent-to-Agent) protocol server that uses AWS Bedrock to answer questions about books via the Gutendex API (Project Gutenberg catalog).

## Context

- **Current State**: Book agent server runs locally via `npm run dev` on ports 4000 (HTTP/JSON-RPC) and 4001 (gRPC)
- **Goal**: Add Docker support similar to existing `agent-chat-server` setup
- **Reference**: The `agent-chat-server` provides a working Docker example, but book-agent-server has different requirements

### Key Differences from agent-chat-server

1. **Dependencies**: Uses A2A SDK (`@a2a-js/sdk`), AWS Bedrock SDK, gRPC
2. **Ports**: HTTP on 4000, gRPC on 4001 (vs agent-chat-server on 3000)
3. **Environment**: Requires AWS credentials (access key, secret, session token, region, model ID)
4. **No Temporal**: Book agent doesn't use Temporal (agent-chat-server does)

## Implementation Steps

### Step 1: Create Dockerfile

Create [book-agent-server/Dockerfile](../book-agent-server/Dockerfile) based on agent-chat-server pattern but adapted for book-agent needs.

**Requirements**:
- Base image: `node:22-alpine` (matches agent-chat-server for consistency)
- Install curl for health checks
- Copy package files and install dependencies
- Copy all TypeScript source files
- Expose ports 4000 (HTTP) and 4001 (gRPC)
- Start command: `npm start` (which runs TypeScript directly via ts-node)

**Key adaptations**:
- Must expose **two ports** (4000 and 4001) not just one
- **No compilation needed**: Uses `ts-node` to run TypeScript directly (workshop/demo scenario)
- Start command: `npm start` (runs `ts-node server.ts`)

**Verification**:
- Dockerfile exists in `book-agent-server/` directory
- Contains all required RUN, COPY, EXPOSE, and CMD directives
- Port 4000 and 4001 are both exposed

### Step 2: Create .dockerignore

Create [book-agent-server/.dockerignore](../book-agent-server/.dockerignore) to exclude unnecessary files from Docker build context.

**Requirements**:
- Exclude `node_modules` (will be installed fresh in container)
- Exclude environment files (`.env`)
- Exclude git files (`.git`)
- Exclude logs (`npm-debug.log`)

**Reference**: Use agent-chat-server/.dockerignore as template

**Verification**:
- File exists and contains standard Node.js exclusions
- Build context will be minimal (faster builds)

### Step 3: Update server.ts for Docker Environment

Modify [book-agent-server/server.ts](../book-agent-server/server.ts) to support environment-based configuration.

**Current hardcoded values**:
- HTTP port: 4000
- gRPC port: 4001
- URLs in AgentCard use `localhost`

**Changes needed**:
```typescript
// Add environment variable support with defaults
const HTTP_PORT = parseInt(process.env.HTTP_PORT || '4000', 10);
const GRPC_PORT = parseInt(process.env.GRPC_PORT || '4001', 10);
const HOST = process.env.HOST || 'localhost';

// Update AgentCard URLs to use environment variables
const bookAgentCard: AgentCard = {
  // ... existing fields ...
  url: `http://${HOST}:${HTTP_PORT}/a2a/jsonrpc`,
  additionalInterfaces: [
    { url: `http://${HOST}:${HTTP_PORT}/a2a/jsonrpc`, transport: 'JSONRPC' },
    { url: `http://${HOST}:${HTTP_PORT}/a2a/rest`, transport: 'HTTP+JSON' },
    { url: `${HOST}:${GRPC_PORT}`, transport: 'GRPC' },
  ],
};

// Update server listen calls
app.listen(HTTP_PORT, '0.0.0.0', () => {
  console.log(`🚀 HTTP Server started on http://0.0.0.0:${HTTP_PORT}`);
});

server.bindAsync(`0.0.0.0:${GRPC_PORT}`, ServerCredentials.createInsecure(), () => {
  console.log(`🚀 gRPC Server started on 0.0.0.0:${GRPC_PORT}`);
});
```

**Why `0.0.0.0`**: In Docker containers, binding to `0.0.0.0` allows external connections. `localhost` only allows connections from within the container.

**Verification**:
- Server starts with environment variables: `HTTP_PORT=4000 GRPC_PORT=4001 npm start`
- Server starts with defaults when no env vars: `npm start`
- Logs show correct ports

### Step 4: Add book-agent-server to docker-compose.yml

Add service definition to [docker-compose.yml](../docker-compose.yml) after the agent-chat-server service.

**Service Configuration**:
```yaml
book-agent-server:
  container_name: book-agent-server
  build:
    context: ./book-agent-server
    dockerfile: Dockerfile
  ports:
    - 4000:4000  # HTTP/JSON-RPC
    - 4001:4001  # gRPC
  environment:
    - HTTP_PORT=4000
    - GRPC_PORT=4001
    - HOST=localhost  # Use localhost so agent is accessible from host machine
    - AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID}
    - AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY}
    - AWS_SESSION_TOKEN=${AWS_SESSION_TOKEN}
    - AWS_REGION=${AWS_REGION:-us-east-1}
    - AWS_MODEL_ID=${AWS_MODEL_ID:-openai.gpt-oss-safeguard-120b}
  networks:
    - docker-network
  healthcheck:
    test: ["CMD-SHELL", "curl -f http://localhost:4000/.well-known/agent-card.json || exit 1"]
    interval: 30s
    timeout: 10s
    retries: 3
```

**Key points**:
- No `depends_on` needed (book-agent-server has no dependencies on other services)
- AWS credentials pass through from `.env` file in repository root (docker-compose automatically loads it)
- Health check uses agent card endpoint (standard A2A endpoint for health verification)
- Uses existing `docker-network`
- Ports 4000 and 4001 do not conflict with other workshop services

**Verification**:
- Service definition is valid YAML
- Indentation matches other services
- All environment variables are defined

### Step 4.5: Create Root .env File

Create a `.env` file in the **repository root** (same directory as `docker-compose.yml`) to store AWS credentials for all Docker services.

**Location**: [.env](../.env) (root of repository)

**Content**:
```bash
# AWS Bedrock Configuration (used by book-agent-server and potentially other services)
AWS_ACCESS_KEY_ID=your_access_key_here
AWS_SECRET_ACCESS_KEY=your_secret_key_here
AWS_SESSION_TOKEN=your_session_token_here
AWS_REGION=us-east-1
AWS_MODEL_ID=openai.gpt-oss-safeguard-120b
```

**Important**: 
- Docker Compose automatically loads this file when running `docker compose up`
- This file should already be in `.gitignore` to avoid committing credentials
- Copy values from workshop config files or existing `book-agent-server/.env` if available
- **Note**: The existing root `.env.example` file should remain untouched

**Verification**:
- `.env` file exists at repository root
- File is listed in `.gitignore`
- Contains all required AWS credentials

### Step 5: Create .env.example

Create [book-agent-server/.env.example](../book-agent-server/.env.example) to document required environment variables for local development (non-Docker).

**Content**:
```bash
# AWS Bedrock Configuration
AWS_ACCESS_KEY_ID=your_access_key_here
AWS_SECRET_ACCESS_KEY=your_secret_key_here
AWS_SESSION_TOKEN=your_session_token_here
AWS_REGION=us-east-1
AWS_MODEL_ID=openai.gpt-oss-safeguard-120b

# Server Configuration (optional - defaults shown)
HTTP_PORT=4000
GRPC_PORT=4001
HOST=localhost
```

**Verification**:
- File exists with all required AWS variables
- Comments explain each variable
- Shows default values where applicable

### Step 6: Update README.md

Update [book-agent-server/README.md](../book-agent-server/README.md) to add Docker instructions.

**Add new section** after "Setup" section:

```markdown
## Docker Deployment

### Using Docker Compose (Recommended)

The book agent server can run as part of the workshop's Docker environment:

1. **Create `.env` file** in the repository root (if not already present):
   ```bash
   # In repository root, create .env file
   cat > .env << EOF
   AWS_ACCESS_KEY_ID=your-key
   AWS_SECRET_ACCESS_KEY=your-secret
   AWS_SESSION_TOKEN=your-token
   AWS_REGION=us-east-1
   AWS_MODEL_ID=openai.gpt-oss-safeguard-120b
   EOF
   ```
   
   **Note**: Docker Compose automatically loads this file. You can also copy values from `1-prompt-engineering/config.properties`.

2. **Start all services** (from workspace root):
   ```bash
   docker compose up --build
   ```

3. **Verify book agent is running**:
   ```bash
   curl http://localhost:4000/.well-known/agent-card.json
   ```

4. **Stop services**:
   ```bash
   docker compose down -v
   ```

### Standalone Docker

To run only the book agent server in Docker:

1. **Build the image**:
   ```bash
   docker build -t book-agent-server .
   ```

2. **Run the container**:
   ```bash
   docker run -p 4000:4000 -p 4001:4001 \
     -e AWS_ACCESS_KEY_ID="your-key" \
     -e AWS_SECRET_ACCESS_KEY="your-secret" \
     -e AWS_SESSION_TOKEN="your-token" \
     -e AWS_REGION="us-east-1" \
     -e AWS_MODEL_ID="openai.gpt-oss-safeguard-120b" \
     book-agent-server
   ```

3. **Test the agent**:
   ```bash
   # Check health
   curl http://localhost:4000/.well-known/agent-card.json
   
   # Test with client (in another terminal)
   npm run client:http
   ```

### Docker Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `AWS_ACCESS_KEY_ID` | Yes | - | AWS access key for Bedrock |
| `AWS_SECRET_ACCESS_KEY` | Yes | - | AWS secret key |
| `AWS_SESSION_TOKEN` | No | - | AWS session token (if using temporary credentials) |
| `AWS_REGION` | No | `us-east-1` | AWS region |
| `AWS_MODEL_ID` | No | `openai.gpt-oss-safeguard-120b` | Bedrock model ID |
| `HTTP_PORT` | No | `4000` | HTTP server port |
| `GRPC_PORT` | No | `4001` | gRPC server port |
| `HOST` | No | `localhost` | Hostname for agent card URLs |
```

**Verification**:
- README contains Docker deployment instructions
- Instructions are clear and tested
- Environment variables are documented

### Step 7: Test Local Docker Build

Build and run the container locally to verify before testing with docker-compose.

**Steps**:
1. Navigate to book-agent-server directory
2. Build image: `docker build -t book-agent-server .`
3. Run container with AWS credentials:
   ```bash
   docker run -p 4000:4000 -p 4001:4001 \
     -e AWS_ACCESS_KEY_ID="$AWS_ACCESS_KEY_ID" \
     -e AWS_SECRET_ACCESS_KEY="$AWS_SECRET_ACCESS_KEY" \
     -e AWS_SESSION_TOKEN="$AWS_SESSION_TOKEN" \
     -e AWS_REGION="us-east-1" \
     book-agent-server
   ```
4. In another terminal, test endpoints:
   ```bash
   # Health check
   curl http://localhost:4000/.well-known/agent-card.json
   
   # Run test client
   cd book-agent-server
   npm run client:http
   ```

**Verification**:
- Docker build completes without errors
- Container starts and shows both server startup logs
- Agent card endpoint returns valid JSON
- HTTP client can connect and get responses
- No errors in container logs

### Step 8: Test with Docker Compose

Test the full docker-compose environment with book-agent-server included.

**Steps**:
1. Navigate to workspace root
2. Ensure `.env` file exists with AWS credentials (see Step 4.5)
3. Start all services: `docker compose up --build`
4. Wait for all health checks to pass
5. Test book-agent-server:
   ```bash
   curl http://localhost:4000/.well-known/agent-card.json
   ```
6. Test from http-client (outside container):
   ```bash
   cd book-agent-server
   npm run client:http
   ```
7. Check logs: `docker compose logs book-agent-server`
8. Clean up: `docker compose down -v`

**Verification**:
- All services start successfully
- book-agent-server shows as healthy in `docker compose ps`
- Agent card endpoint accessible from host
- Client can successfully communicate with containerized server
- No connection errors in logs

## Validation Checklist

After completing all steps, verify:

- [ ] Dockerfile exists and builds successfully
- [ ] .dockerignore excludes correct files
- [ ] server.ts uses environment variables for configuration
- [ ] docker-compose.yml includes book-agent-server service
- [ ] Root `.env` file created with AWS credentials
- [ ] Root `.env` file is in .gitignore
- [ ] .env.example documents all required variables
- [ ] README.md has Docker deployment instructions
- [ ] Local Docker build works: `docker build -t book-agent-server .`
- [ ] Local Docker run works with port forwarding
- [ ] Agent card endpoint responds: `curl http://localhost:4000/.well-known/agent-card.json`
- [ ] HTTP client can connect: `npm run client:http`
- [ ] Docker compose builds and starts all services
- [ ] Health check passes in docker-compose
- [ ] No errors in `docker compose logs book-agent-server`
- [ ] Server binds to 0.0.0.0 (not localhost) for external access
- [ ] Both ports 4000 and 4001 are accessible
- [ ] AWS credentials properly pass through to container

## Success Criteria

The implementation is complete when:

1. **Standalone Docker works**: Can build and run book-agent-server in Docker with manual docker commands
2. **Docker Compose works**: Book-agent-server starts, passes health checks, and operates correctly in docker-compose environment
3. **Network access**: Agent is accessible from host machine on ports 4000 and 4001
4. **Client connectivity**: HTTP and gRPC clients can successfully communicate with containerized agent
5. **Documentation**: README clearly explains Docker deployment options
6. **Environment parity**: Container behavior matches local development behavior

## Questions

No outstanding questions at this time. All previous questions have been addressed:

1. ✅ **Build approach**: Use ts-node to run TypeScript directly (no compilation)
2. ✅ **Model ID**: Use `openai.gpt-oss-safeguard-120b` throughout
3. ✅ **HOST variable**: Use `localhost` for host machine accessibility
4. ✅ **.env file**: Create root `.env` as needed, leave `.env.example` untouched
5. ✅ **TypeScript config**: No compilation needed with ts-node approach