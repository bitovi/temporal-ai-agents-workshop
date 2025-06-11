# OpenTelemetry + Langfuse Integration for AI Agents

This project provides comprehensive observability for your AI agent system by capturing LLM interactions and sending them to Langfuse via OpenTelemetry traces.

## 🎯 What We've Implemented

### Core Components

1. **`OTelLLMTracer`** - Main OpenTelemetry tracer for LLM operations
2. **`TracedBedrockProvider`** - AWS Bedrock provider with automatic tracing
3. **`TracedOllamaProvider`** - Ollama provider with automatic tracing
4. **`LangfuseProvider`** - Enhanced Langfuse client with OpenTelemetry integration

### Key Features

✅ **Automatic LLM Tracing** - All LLM calls are automatically traced  
✅ **Tool Usage Tracking** - Captures tool calls and results  
✅ **Error Handling** - Proper error tracing and reporting  
✅ **Performance Metrics** - Duration, token counts, model information  
✅ **Custom Context** - Add business context to traces  
✅ **Langfuse Integration** - Direct export to Langfuse via OTLP  

## 🚀 Quick Start

### 1. Configuration

Add to your `config.properties`:

```properties
# Langfuse Configuration (Required)
LANGFUSE_SECRET_KEY=sk-lf-your-secret-key
LANGFUSE_PUBLIC_KEY=pk-lf-your-public-key
LANGFUSE_HOST=http://localhost:3000

# AWS Bedrock Configuration (Optional)
AWS_ACCESS_KEY_ID=your-access-key
AWS_SECRET_ACCESS_KEY=your-secret-key
AWS_SESSION_TOKEN=your-session-token
AWS_MODEL_ARN=arn:aws:bedrock:us-east-2:account:inference-profile/model-id
AWS_MODEL_ID=your-model-id

# Ollama Configuration (Optional)
OLLAMA_HOST=http://localhost:11434/
OLLAMA_MODEL_ID=mistral:latest
```

### 2. Test the Integration

```bash
# Run simple OpenTelemetry test (recommended first step)
./run-simple-otel-test.sh

# Run full LLM tracing example (requires valid AWS/Ollama credentials)
./run-otel-tracing-example.sh
```

### 3. View Traces in Langfuse

1. Open your Langfuse dashboard: `http://localhost:3000`
2. Navigate to the "Traces" section
3. Look for traces with LLM operations

## 📊 What Gets Traced

### LLM Request Data
- **System**: LLM provider (aws_bedrock, ollama)
- **Model**: Model identifier and version
- **Prompt**: Input text or conversation
- **Parameters**: Temperature, max tokens, etc.
- **Tools**: Available tools and their usage

### LLM Response Data  
- **Completion**: Model response text
- **Tokens**: Prompt, completion, and total token counts
- **Performance**: Request duration and timing
- **Errors**: Exception details and stack traces

### Business Context
- **User ID**: User identifier
- **Session ID**: Conversation session
- **Conversation ID**: Unique conversation tracking
- **Custom Attributes**: Any business-specific data

## 💻 Usage Examples

### Basic Usage (Automatic Tracing)

```java
// Replace your existing providers with traced versions
TracedBedrockProvider bedrockProvider = new TracedBedrockProvider();
TracedOllamaProvider ollamaProvider = new TracedOllamaProvider();

// Use normally - tracing happens automatically
ArrayList<MessageRecord> messages = new ArrayList<>();
messages.add(new MessageRecord("user", "Hello, how are you?"));

MessageRecord response = bedrockProvider.chat(messages);
```

### Custom Tracing with Business Context

```java
OTelLLMTracer tracer = OTelLLMTracer.getInstance();

// Create a span with business context
Span span = tracer.startChatSpan("aws_bedrock", "claude", "conversation_123");

try (Scope scope = tracer.withSpan(span)) {
    // Add business attributes
    span.setAttribute("user_id", "user_456");
    span.setAttribute("session_id", "session_789");
    span.setAttribute("conversation_type", "customer_support");
    
    // Your LLM call
    MessageRecord response = bedrockProvider.chat(messages);
    
    // Add response context
    span.setAttribute("issue_resolved", true);
    
    tracer.finishSpanSuccess(span);
} catch (Exception e) {
    tracer.finishSpanError(span, e);
    throw e;
}
```

### Tool Calling (Automatically Traced)

```java
// Tool usage is automatically captured
ArrayList<MessageRecord> messages = new ArrayList<>();
messages.add(new MessageRecord("user", "Calculate the cosine of 1.57 radians"));

// This traces both the LLM call and tool execution
MessageRecord response = bedrockProvider.chatWithAllTools(messages);
```

## 🏗️ Integration with Existing Code

### Temporal Activities

```java
public class MyActivitiesImpl implements MyActivities {
    private TracedBedrockProvider provider = new TracedBedrockProvider();
    
    @Override
    public String processQuery(String query) {
        // Automatically traced within Temporal workflow context
        return provider.completion(query);
    }
}
```

### MCP Tool Integration

```java
public MessageRecord processWithMCPTools(String userQuery) {
    TracedBedrockProvider provider = new TracedBedrockProvider();
    
    ArrayList<MessageRecord> messages = new ArrayList<>();
    messages.add(new MessageRecord("user", userQuery));
    
    // MCP tool usage is traced automatically
    return provider.chatWithAllTools(messages);
}
```

## 📁 Project Structure

```
src/main/java/bitovi/
├── providers/
│   ├── OTelLLMTracer.java           # Core OpenTelemetry tracer
│   ├── TracedBedrockProvider.java   # AWS Bedrock with tracing
│   ├── TracedOllamaProvider.java    # Ollama with tracing
│   └── LangfuseProvider.java        # Enhanced Langfuse client
├── examples/
│   ├── SimpleOTelTest.java          # Basic tracing test
│   └── OTelTracingExample.java      # Full LLM tracing demo
└── scripts/
    ├── run-simple-otel-test.sh      # Test OpenTelemetry setup
    └── run-otel-tracing-example.sh  # Full demo script
```

## 🔧 Configuration Details

### OpenTelemetry Endpoint

The integration automatically configures the OTLP endpoint as:
```
{LANGFUSE_HOST}/api/public/otel
```

### Batch Processing

Traces are batched and sent asynchronously:
- **Batch Size**: 512 spans
- **Schedule Delay**: 5 seconds
- **Content Type**: `application/x-protobuf`

### Resource Attributes

Service-level attributes:
- **Service Name**: `bitovi-ai-agents`
- **Service Version**: `1.0.0`
- **Instance ID**: `agent-instance-1`

## 🐛 Troubleshooting

### Common Issues

1. **No traces in Langfuse**:
   ```bash
   # Check configuration
   grep LANGFUSE_HOST config.properties
   
   # Test basic connectivity
   ./run-simple-otel-test.sh
   ```

2. **Build failures**:
   ```bash
   # Clean and rebuild
   mvn clean compile
   ```

3. **AWS credential errors**:
   - Expected if AWS credentials are expired/invalid
   - Ollama tracing should still work
   - Basic OpenTelemetry test doesn't require AWS

4. **404 errors from Langfuse**:
   - Check that Langfuse server is running
   - Verify OTLP endpoint is correct: `{LANGFUSE_HOST}/api/public/otel`

### Debug Logging

Enable OpenTelemetry debug logs:
```java
System.setProperty("otel.java.global-autoconfigure.enabled", "true");
System.setProperty("otel.logs.exporter", "console");
```

## 📈 Benefits

### For Development
- **Debug LLM Issues**: See exact prompts and responses
- **Performance Optimization**: Track token usage and latency
- **Error Analysis**: Detailed error traces with context

### For Production
- **Monitoring**: Real-time observability of AI operations
- **Analytics**: Understand usage patterns and costs
- **Alerting**: Get notified of performance issues

### For Business
- **Usage Tracking**: Monitor AI feature adoption
- **Cost Analysis**: Track token consumption and costs
- **Quality Metrics**: Measure response quality and user satisfaction

## 🚀 Next Steps

1. **Test the Integration**:
   ```bash
   ./run-simple-otel-test.sh
   ```

2. **Start Using Traced Providers**:
   Replace `BedrockProvider` with `TracedBedrockProvider` in your code

3. **Add Custom Context**:
   Include user IDs, session IDs, and business context in your traces

4. **Monitor in Production**:
   Set up alerts and dashboards in Langfuse

5. **Optimize Performance**:
   Use trace data to optimize prompts and reduce token usage

## 📚 Documentation

- **[OTEL_LANGFUSE_INTEGRATION.md](OTEL_LANGFUSE_INTEGRATION.md)** - Detailed technical documentation
- **[Simple Test Example](src/main/java/bitovi/examples/SimpleOTelTest.java)** - Basic usage
- **[Full LLM Example](src/main/java/bitovi/examples/OTelTracingExample.java)** - Advanced patterns

---

🎉 **You now have comprehensive observability for your AI agent system!** 

Your LLM interactions will be automatically traced and sent to Langfuse, giving you complete visibility into your AI operations.
