# OpenTelemetry + Langfuse Integration for AI Agents

This integration provides comprehensive observability for your AI agent system by capturing LLM interactions and sending them to Langfuse via OpenTelemetry traces.

## Overview

The integration captures:
- **LLM Requests**: Prompts, model parameters, conversation context
- **LLM Responses**: Completions, token usage, timing metrics  
- **Tool Usage**: Tool calls, arguments, and results
- **Error Handling**: Exceptions and failure scenarios
- **Performance Metrics**: Duration, token counts, model information

## Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Your AI       │───▶│  OTelLLMTracer   │───▶│   Langfuse      │
│   Application   │    │  (OpenTelemetry) │    │   Dashboard     │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                              │
                              ▼
                       ┌──────────────────┐
                       │ OTLP HTTP Export │
                       │ /api/public/otel │
                       └──────────────────┘
```

## Configuration

### 1. Environment Variables

Add these to your `config.properties`:

```properties
# Langfuse Configuration
LANGFUSE_SECRET_KEY=sk-lf-your-secret-key
LANGFUSE_PUBLIC_KEY=pk-lf-your-public-key
LANGFUSE_HOST=http://localhost:3000

# AWS Bedrock Configuration (if using)
AWS_ACCESS_KEY_ID=your-access-key
AWS_SECRET_ACCESS_KEY=your-secret-key
AWS_SESSION_TOKEN=your-session-token
AWS_MODEL_ARN=arn:aws:bedrock:us-east-2:account:inference-profile/model-id
AWS_MODEL_ID=your-model-id

# Ollama Configuration (if using)
OLLAMA_HOST=http://localhost:11434/
OLLAMA_MODEL_ID=mistral:latest
```

### 2. Dependencies

The following dependencies are automatically included:

```xml
<!-- OpenTelemetry Core -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk</artifactId>
</dependency>

<!-- OTLP HTTP Exporter -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>

<!-- Langfuse Client -->
<dependency>
    <groupId>com.langfuse</groupId>
    <artifactId>langfuse-java</artifactId>
    <version>0.0.6</version>
</dependency>
```

## Usage

### 1. Basic Usage with Traced Providers

```java
import bitovi.providers.TracedBedrockProvider;
import bitovi.providers.TracedOllamaProvider;
import bitovi.records.MessageRecord;

// Initialize traced providers
TracedBedrockProvider bedrockProvider = new TracedBedrockProvider();
TracedOllamaProvider ollamaProvider = new TracedOllamaProvider();

// Use normally - tracing happens automatically
ArrayList<MessageRecord> messages = new ArrayList<>();
messages.add(new MessageRecord("user", "Hello, how are you?"));

MessageRecord response = bedrockProvider.chat(messages);
System.out.println(response.content());
```

### 2. Custom Tracing with Additional Context

```java
import bitovi.providers.OTelLLMTracer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

OTelLLMTracer tracer = OTelLLMTracer.getInstance();

// Create a custom span with business context
Span span = tracer.startChatSpan("aws_bedrock", "claude", "conversation_123");

try (Scope scope = tracer.withSpan(span)) {
    // Add custom attributes
    span.setAttribute("user_id", "user_456");
    span.setAttribute("session_id", "session_789");
    span.setAttribute("conversation_type", "customer_support");
    
    // Your LLM call
    MessageRecord response = bedrockProvider.chat(messages);
    
    // Add response context
    span.setAttribute("response_sentiment", "positive");
    span.setAttribute("issue_resolved", true);
    
    tracer.finishSpanSuccess(span);
} catch (Exception e) {
    tracer.finishSpanError(span, e);
    throw e;
}
```

### 3. Tool Calling with Tracing

```java
// Tool calls are automatically traced
ArrayList<MessageRecord> messages = new ArrayList<>();
messages.add(new MessageRecord("user", "Calculate the cosine of 1.57 radians"));

// This will trace both the LLM call and tool usage
MessageRecord response = bedrockProvider.chatWithAllTools(messages);
```

### 4. Lambda-style Tracing

```java
// Trace any operation with a lambda
String result = tracer.trace("custom.operation", "bedrock", "claude", span -> {
    span.setAttribute("operation_type", "summarization");
    return bedrockProvider.completion("Summarize this text: " + longText);
});
```

## Captured Trace Data

### LLM Request Attributes
- `llm.system`: LLM provider (aws_bedrock, ollama)
- `llm.request.model`: Model identifier 
- `llm.request.type`: Operation type (chat, completion, embedding)
- `llm.prompt`: Input prompt or conversation
- `llm.temperature`: Model temperature setting
- `llm.max_tokens`: Maximum tokens setting
- `llm.tools`: Available tools (comma-separated)
- `llm.conversation.id`: Conversation identifier
- `llm.user.id`: User identifier

### LLM Response Attributes  
- `llm.completion`: Model response text
- `llm.response.model`: Actual model used
- `llm.usage.prompt_tokens`: Tokens in prompt
- `llm.usage.completion_tokens`: Tokens in response
- `llm.usage.total_tokens`: Total tokens used
- `llm.tool_results`: Tool execution results

### Provider-Specific Attributes

**AWS Bedrock:**
- `operation`: bedrock operation type
- `recursion_depth`: Tool calling depth
- `tools_used`: Whether tools were invoked

**Ollama:**
- `ollama_host`: Ollama server endpoint
- `message_count`: Number of messages
- `total_duration_ns`: Total processing time
- `eval_count`: Evaluation token count

## Running the Example

1. **Start your Langfuse server** (if running locally):
   ```bash
   docker run -p 3000:3000 langfuse/langfuse
   ```

2. **Configure your environment** in `config.properties`

3. **Run the tracing example**:
   ```bash
   ./run-otel-tracing-example.sh
   ```

4. **View traces in Langfuse** at `http://localhost:3000`

## Integration with Existing Code

### Replace Existing Providers

```java
// Before: 
BedrockProvider provider = new BedrockProvider();

// After:
TracedBedrockProvider provider = new TracedBedrockProvider();
// All existing code works the same, now with tracing!
```

### Add to Temporal Activities

```java
public class MyActivitiesImpl implements MyActivities {
    private TracedBedrockProvider bedrockProvider = new TracedBedrockProvider();
    
    @Override
    public String processQuery(String query) {
        // This will automatically be traced
        return bedrockProvider.completion(query);
    }
}
```

### Add to MCP Integration

```java
public MessageRecord processWithMCPTools(String userQuery) {
    TracedBedrockProvider provider = new TracedBedrockProvider();
    
    ArrayList<MessageRecord> messages = new ArrayList<>();
    messages.add(new MessageRecord("user", userQuery));
    
    // MCP tool usage will be traced
    return provider.chatWithAllTools(messages);
}
```

## Langfuse Dashboard Features

Once traces are flowing to Langfuse, you can:

1. **View Individual Traces**: See complete request/response flows
2. **Monitor Performance**: Track latency and token usage
3. **Debug Issues**: Examine error traces and exceptions  
4. **Analyze Usage**: Understand model usage patterns
5. **Set Alerts**: Get notified of performance issues
6. **Export Data**: Extract traces for further analysis

## Troubleshooting

### Common Issues

1. **No traces appearing in Langfuse**:
   - Check `LANGFUSE_HOST` configuration
   - Verify Langfuse server is running
   - Check network connectivity to OTLP endpoint

2. **Build failures**:
   - Ensure all OpenTelemetry dependencies are present
   - Check Java version compatibility (requires Java 11+)

3. **Authentication errors**:
   - Verify `LANGFUSE_SECRET_KEY` and `LANGFUSE_PUBLIC_KEY`
   - Check Langfuse server configuration

4. **Performance impact**:
   - Tracing adds minimal overhead (~1-5ms per operation)
   - Traces are batched and sent asynchronously
   - Configure batch settings if needed

### Debug Logging

Enable OpenTelemetry debug logging:

```java
System.setProperty("otel.java.global-autoconfigure.enabled", "true");
System.setProperty("otel.logs.exporter", "console");
```

## Advanced Configuration

### Custom Span Processors

```java
// Add custom processing before spans are exported
SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
    .addSpanProcessor(BatchSpanProcessor.builder(spanExporter)
        .setMaxExportBatchSize(512)           // Batch size
        .setExportTimeout(Duration.ofSeconds(30))  // Export timeout
        .setScheduleDelay(Duration.ofSeconds(5))   // Batch delay
        .build())
    .build();
```

### Resource Attributes

```java
// Add service-level attributes
Resource resource = Resource.create(Attributes.of(
    ResourceAttributes.SERVICE_NAME, "my-ai-service",
    ResourceAttributes.SERVICE_VERSION, "2.0.0",
    ResourceAttributes.DEPLOYMENT_ENVIRONMENT, "production"
));
```

## Best Practices

1. **Use Traced Providers**: Replace standard providers with traced versions
2. **Add Business Context**: Include user IDs, session IDs, and workflow context
3. **Handle Errors Gracefully**: Always finish spans, even on errors
4. **Monitor Performance**: Watch for trace overhead in production
5. **Secure Credentials**: Keep Langfuse keys secure and rotated
6. **Batch Configuration**: Tune batch settings for your traffic volume

This integration provides comprehensive observability for your AI agent system, helping you understand performance, debug issues, and optimize your LLM usage patterns.
