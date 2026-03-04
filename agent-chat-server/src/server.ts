import express from "express";
import { EventEmitter } from "events";
import { randomUUID } from "node:crypto";
import { Connection, Client } from "@temporalio/client";
import dotenv from "dotenv";
import path from "path";
import { fileURLToPath } from "url";

dotenv.config();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export const eventEmitter = new EventEmitter();

const app = express();
app.use(express.json());
app.use(express.static(path.join(__dirname, "../public")));

const TEMPORAL_HOST_PORT = process.env.TEMPORAL_HOST_PORT || "localhost:7233";
const TEMPORAL_NAMESPACE = process.env.TEMPORAL_NAMESPACE || "default";

const TEMPORAL_WORKFLOW_NAME =
  process.env.TEMPORAL_WORKFLOW_NAME || "agentWorkflow";
const TEMPORAL_MESSAGE_SIGNAL =
  process.env.TEMPORAL_MESSAGE_SIGNAL || "message";
const TEMPORAL_EXIT_SIGNAL = process.env.TEMPORAL_EXIT_SIGNAL || "exit";
const TEMPORAL_CONTINUE_AS_NEW_SIGNAL =
  process.env.TEMPORAL_CONTINUE_AS_NEW_SIGNAL || "continueAsNew";

const workflowSessions: Map<string, any> = new Map();
let connection: any;
let client: any;

// Initialize Temporal client
async function initTemporal() {
  connection = await Connection.connect({
    address: TEMPORAL_HOST_PORT,
  });
  client = new Client({
    connection,
    namespace: TEMPORAL_NAMESPACE,
  });
}

// POST /api/conversations - Start new conversation
app.post("/api/conversations", async (req, res) => {
  try {
    const conversationId = randomUUID();
    const handle = await client.workflow.start(TEMPORAL_WORKFLOW_NAME, {
      args: [{}],
      taskQueue: process.env.TEMPORAL_TASK_QUEUE || "bitovi-ai-agents-workshop",
      workflowId: `agent-workflow-${conversationId}`,
    });
    workflowSessions.set(conversationId, handle);
    res.json({ conversationId, workflowId: handle.workflowId });
  } catch (error) {
    res.status(500).json({ error: (error as Error).message });
  }
});

// GET /api/conversations - List active conversations
app.get("/api/conversations", (req, res) => {
  const conversations = Array.from(workflowSessions.keys());
  res.json({ conversations });
});

// POST /api/conversations/:id/message - Send message
app.post("/api/conversations/:id/message", async (req, res) => {
  try {
    const { id } = req.params;
    const { name, message } = req.body;

    if (!name || !message) {
      return res.status(400).json({ error: "name and message required" });
    }

    const handle = workflowSessions.get(id);
    if (!handle) {
      return res.status(404).json({ error: "Conversation not found" });
    }

    await handle.signal(TEMPORAL_MESSAGE_SIGNAL, {
      name,
      message,
      date: new Date().toISOString(),
    });

    eventEmitter.emit("bot-event", {
      type: "user_message",
      message: message,
      timestamp: Date.now(),
    });

    res.json({ success: true });
  } catch (error) {
    res.status(500).json({ error: (error as Error).message });
  }
});

// POST /api/conversations/:id/exit - End conversation
app.post("/api/conversations/:id/exit", async (req, res) => {
  try {
    const { id } = req.params;
    const handle = workflowSessions.get(id);
    if (!handle) {
      return res.status(404).json({ error: "Conversation not found" });
    }

    await handle.signal(TEMPORAL_EXIT_SIGNAL);
    const result = await handle.result();
    workflowSessions.delete(id);
    res.json({ success: true, usage: result.usage });
  } catch (error) {
    res.status(500).json({ error: (error as Error).message });
  }
});

// POST /api/conversations/:id/compact - Trigger compaction
app.post("/api/conversations/:id/compact", async (req, res) => {
  try {
    const { id } = req.params;
    const handle = workflowSessions.get(id);
    if (!handle) {
      return res.status(404).json({ error: "Conversation not found" });
    }

    await handle.signal(TEMPORAL_CONTINUE_AS_NEW_SIGNAL);
    res.json({ success: true });
  } catch (error) {
    res.status(500).json({ error: (error as Error).message });
  }
});

// POST /api/emit-event - Receive events from worker activities
app.post("/api/emit-event", (req, res) => {
  const eventData = req.body;
  eventEmitter.emit("bot-event", eventData);
  res.json({ success: true });
});

app.get("/events", (req, res) => {
  res.writeHead(200, {
    "Content-Type": "text/event-stream",
    "Cache-Control": "no-cache",
    Connection: "keep-alive",
    "Access-Control-Allow-Origin": "*",
  });

  res.write(
    `data: ${JSON.stringify({ type: "connected", message: "SSE connected" })}\n\n`,
  );

  const listener = (data: any) => {
    res.write(`data: ${JSON.stringify(data)}\n\n`);
  };

  eventEmitter.on("bot-event", listener);

  req.on("close", () => {
    eventEmitter.off("bot-event", listener);
  });
});

export default app;

// If this file is run directly, start the server
if (import.meta.url === `file://${process.argv[1]}`) {
  async function main() {
    try {
      await initTemporal();
      console.log("Temporal client initialized");

      const PORT = process.env.PORT || 3000;
      app.listen(PORT, () => {
        console.log(`Server running on port ${PORT}`);
      });
    } catch (error) {
      console.error("Failed to start server:", error);
      process.exit(1);
    }
  }

  // Handle graceful shutdown
  process.on("SIGINT", () => {
    console.log("\nReceived SIGINT, shutting down gracefully...");
    process.exit(0);
  });

  process.on("SIGTERM", () => {
    console.log("\nReceived SIGTERM, shutting down gracefully...");
    process.exit(0);
  });

  main().catch((error) => {
    console.error("Unexpected error starting server:", error);
    process.exit(1);
  });
}
