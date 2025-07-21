```typescript
const app = express();
app.use(express.json());

const server = new McpServer({
  name: "demo-mcp-server",
  version: "1.0.0"
});

app.post('/mcp', async (req: Request, res: Response) => {
    const transport: StreamableHTTPServerTransport = new StreamableHTTPServerTransport({
      sessionIdGenerator: undefined,
    });
    res.on('close', () => {
      transport.close();
    });
    await server.connect(transport);
    await transport.handleRequest(req, res, req.body);
});

app.listen(3000);
```
