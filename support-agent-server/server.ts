/**
 * Riot Games Support Agent — A2A (Agent-to-Agent) Server
 *
 * This is a remote A2A agent that handles billing inquiries, refunds, and
 * account issues. It demonstrates:
 *
 *   1. Agent Card — advertises capabilities at .well-known/agent-card.json
 *   2. Multi-turn conversations — uses `input-required` state to pause and
 *      ask the caller for identity verification, then resumes on follow-up
 *   3. Artifacts — emits structured refund receipts as DataPart artifacts
 *   4. Opacity — the calling agent can’t see this agent’s internal tools or
 *      reasoning; it only sees status updates and the final answer
 *
 * Transports: JSON-RPC (port 4000), REST (port 4000), gRPC (port 4001)
 */

import dotenv from "dotenv";
import express from "express";
import { Server, ServerCredentials } from "@grpc/grpc-js";
import { AgentCard, AGENT_CARD_PATH } from "@a2a-js/sdk";

dotenv.config();
import { DefaultRequestHandler, InMemoryTaskStore } from "@a2a-js/sdk/server";
import {
  agentCardHandler,
  jsonRpcHandler,
  restHandler,
  UserBuilder,
} from "@a2a-js/sdk/server/express";
import { grpcService, A2AService } from "@a2a-js/sdk/server/grpc";
import { SupportAgentExecutor } from "./executor";

// Environment configuration
const HTTP_PORT = parseInt(process.env.HTTP_PORT || "4000", 10);
const GRPC_PORT = parseInt(process.env.GRPC_PORT || "4001", 10);
const HOST = process.env.HOST || "localhost";

// 1. Agent Card
const supportAgentCard: AgentCard = {
  name: "Riot Games Support Agent",
  description:
    "Handles billing inquiries, refunds, and account issues for Riot Games.",
  protocolVersion: "0.3.0",
  version: "0.1.0",
  url: `http://${HOST}:${HTTP_PORT}/a2a/jsonrpc`,
  skills: [
    {
      id: "billing",
      name: "Billing Support",
      description: "Refunds, duplicate charges, payment issues",
      tags: ["billing", "refunds"],
    },
    {
      id: "account",
      name: "Account Support",
      description: "Account verification, password resets",
      tags: ["account"],
    },
  ],
  capabilities: {
    pushNotifications: false,
    streaming: true,
  },
  defaultInputModes: ["text"],
  defaultOutputModes: ["text", "data"],
  additionalInterfaces: [
    { url: `http://${HOST}:${HTTP_PORT}/a2a/jsonrpc`, transport: "JSONRPC" },
    { url: `http://${HOST}:${HTTP_PORT}/a2a/rest`, transport: "HTTP+JSON" },
    { url: `${HOST}:${GRPC_PORT}`, transport: "GRPC" },
  ],
  supportsAuthenticatedExtendedCard: true,
};

// 2. Set up and run the server
const agentExecutor = new SupportAgentExecutor();
const requestHandler = new DefaultRequestHandler(
  supportAgentCard,
  new InMemoryTaskStore(),
  agentExecutor,
);

const app = express();

app.use((req, res, next) => {
  console.log(`[HTTP] ${req.method} ${req.path}`);
  return next();
});

// 3. Set up the HTTP routes for the agent card and A2A Protocol over HTTP
app.use(
  `/${AGENT_CARD_PATH}`,
  agentCardHandler({ agentCardProvider: requestHandler }),
);

app.use(
  "/a2a/jsonrpc",
  jsonRpcHandler({ requestHandler, userBuilder: UserBuilder.noAuthentication }),
);

app.use(
  "/a2a/rest",
  restHandler({ requestHandler, userBuilder: UserBuilder.noAuthentication }),
);

app.listen(HTTP_PORT, "0.0.0.0", () => {
  console.log(`🚀 HTTP Server started on http://0.0.0.0:${HTTP_PORT}`);
});

// 4. Set up the gRPC server for the A2A protocol over gRPC
const server = new Server();
server.addService(
  A2AService,
  grpcService({
    requestHandler,
    userBuilder: UserBuilder.noAuthentication,
  }),
);
server.bindAsync(
  `0.0.0.0:${GRPC_PORT}`,
  ServerCredentials.createInsecure(),
  () => {
    console.log(`🚀 gRPC Server started on 0.0.0.0:${GRPC_PORT}`);
  },
);
