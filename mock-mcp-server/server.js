import express from "express";
import dotenv from "dotenv";
import { z } from "zod";

import { randomUUID } from "node:crypto";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { SSEServerTransport } from "@modelcontextprotocol/sdk/server/sse.js";
import { isInitializeRequest } from "@modelcontextprotocol/sdk/types.js"

dotenv.config();

// Map to store transports by session ID
const transports = {};
const sseTransports = {};

function main() {
    const server = createMcpServer();
    const app = express();
    app.use(express.json());

    app.post('/mcp', async (req, res) => {
        const sessionId = req.headers['mcp-session-id']
        let transport;

        if (sessionId && transports[sessionId]) {
            // Reuse existing transport
            transport = transports[sessionId];
        } else if (!sessionId && isInitializeRequest(req.body)) {
            transport = new StreamableHTTPServerTransport({
                sessionIdGenerator: () => randomUUID(),
                onsessioninitialized: (sessionId) => {
                    // Store the transport by session ID
                    transports[sessionId] = transport;
                },
                enableDnsRebindingProtection: false,
                allowedHosts: ['127.0.0.1'],
            });

            transport.onclose = () => {
                if (transport.sessionId) {
                    delete transports[transport.sessionId];
                }
            };

            await server.connect(transport);
        } else {
            throw new Error('Invalid Session');
        }

        await transport.handleRequest(req, res, req.body);
    });


    // Handle GET requests for server-to-client notifications via SSE
    app.all('/mcp', async (req, res) => {
        const sessionId = req.headers['mcp-session-id'];
        if (!sessionId || !transports[sessionId]) {
            throw new Error('Invalid Session');
        }

        const transport = transports[sessionId];
        await transport.handleRequest(req, res);
    });

    // Legacy SSE endpoint for older clients
    app.get('/sse', async (req, res) => {
        // Create SSE transport for legacy clients
        const transport = new SSEServerTransport('/messages', res);
        sseTransports[transport.sessionId] = transport;

        res.on("close", () => {
            delete sseTransports[transport.sessionId];
        });

        await server.connect(transport);
    });

    // Legacy message endpoint for older clients
    app.post('/messages', async (req, res) => {
        const sessionId = req.query.sessionId;
        const transport = sseTransports[sessionId];
        if (transport) {
            await transport.handlePostMessage(req, res, req.body);
        } else {
            throw new Error('Invalid Session');
        }
    });

    const port = parseInt(process.env.PORT || "8090", 10);
    app.listen(port, () => {
        console.log(`MCP server is running on port ${port}`);
    });
}

function createMcpServer() {
    const server = new McpServer({
        name: "mcp-server",
        version: "1.0.0",
    });

    // Register tools and prompts
    server.registerTool("weather-by-zip-code",
        {
            title: "Weather by Zip Code",
            description: "Get current weather for a zip code",
            inputSchema: { zipCode: z.string() }
        },
        async ({ zipCode }) => {
            console.log(`Received weather request for zip code: ${zipCode}`);

            // Simulate a weather response
            const weatherData = {
                "coord": {
                    "lon": -122.4167,
                    "lat": 37.7813
                },
                "weather": [
                    {
                        "id": 801,
                        "main": "Clouds",
                        "description": "few clouds",
                        "icon": "02d"
                    }
                ],
                "base": "stations",
                "main": {
                    "temp": 291.97,
                    "feels_like": 291.84,
                    "temp_min": 289.87,
                    "temp_max": 295.05,
                    "pressure": 1012,
                    "humidity": 74,
                    "sea_level": 1012,
                    "grnd_level": 1009
                },
                "visibility": 10000,
                "wind": {
                    "speed": 6.17,
                    "deg": 330
                },
                "clouds": {
                    "all": 20
                },
                "dt": 1752258201,
                "sys": {
                    "type": 2,
                    "id": 2017837,
                    "country": "US",
                    "sunrise": 1752238625,
                    "sunset": 1752291176
                },
                "timezone": -25200,
                "id": 0,
                "name": "San Francisco",
                "cod": 200
            }

            console.log(`Weather data retrieved: ${JSON.stringify(weatherData)}`);

            return {
                content: [
                    { type: "text", text: JSON.stringify(weatherData, null, 2) },
                ],
            };
        }
    );

    return server;
}

// Execute
main();