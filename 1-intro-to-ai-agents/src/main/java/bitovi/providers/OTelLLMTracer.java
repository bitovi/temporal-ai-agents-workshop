package bitovi.providers;

import bitovi.Config;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ResourceAttributes;

import java.time.Duration;
import java.util.List;

/**
 * OpenTelemetry tracer for LLM interactions that sends traces to Langfuse
 */
public class OTelLLMTracer {
    private static OTelLLMTracer instance;
    private OpenTelemetry openTelemetry;
    private Tracer tracer;

    // LLM-specific attribute keys following OpenTelemetry semantic conventions
    public static final AttributeKey<String> LLM_SYSTEM = AttributeKey.stringKey("llm.system");
    public static final AttributeKey<String> LLM_REQUEST_MODEL = AttributeKey.stringKey("llm.request.model");
    public static final AttributeKey<String> LLM_REQUEST_TYPE = AttributeKey.stringKey("llm.request.type");
    public static final AttributeKey<String> LLM_RESPONSE_MODEL = AttributeKey.stringKey("llm.response.model");
    public static final AttributeKey<Long> LLM_USAGE_PROMPT_TOKENS = AttributeKey.longKey("llm.usage.prompt_tokens");
    public static final AttributeKey<Long> LLM_USAGE_COMPLETION_TOKENS = AttributeKey
            .longKey("llm.usage.completion_tokens");
    public static final AttributeKey<Long> LLM_USAGE_TOTAL_TOKENS = AttributeKey.longKey("llm.usage.total_tokens");
    public static final AttributeKey<String> LLM_PROMPT = AttributeKey.stringKey("llm.prompt");
    public static final AttributeKey<String> LLM_COMPLETION = AttributeKey.stringKey("llm.completion");
    public static final AttributeKey<Double> LLM_TEMPERATURE = AttributeKey.doubleKey("llm.temperature");
    public static final AttributeKey<Long> LLM_MAX_TOKENS = AttributeKey.longKey("llm.max_tokens");
    public static final AttributeKey<String> LLM_TOOLS = AttributeKey.stringKey("llm.tools");
    public static final AttributeKey<String> LLM_CONVERSATION_ID = AttributeKey.stringKey("llm.conversation.id");
    public static final AttributeKey<String> LLM_USER_ID = AttributeKey.stringKey("llm.user.id");

    private OTelLLMTracer() {
        initializeOpenTelemetry();
    }

    public static synchronized OTelLLMTracer getInstance() {
        if (instance == null) {
            instance = new OTelLLMTracer();
        }
        return instance;
    }

    private void initializeOpenTelemetry() {
        try {
            String langfuseHost = Config.getProperty("LANGFUSE_HOST");
            if (langfuseHost == null) {
                System.err.println("LANGFUSE_HOST not configured, OpenTelemetry tracing disabled");
                return;
            }

            // Create OTLP exporter pointing to Langfuse
            String otlpEndpoint = langfuseHost.replaceAll("\"", "") + "/api/public/otel";

            OtlpHttpSpanExporter spanExporter = OtlpHttpSpanExporter.builder()
                    .setEndpoint(otlpEndpoint)
                    .addHeader("Content-Type", "application/x-protobuf")
                    .setTimeout(Duration.ofSeconds(30))
                    .build();

            // Create resource with service information
            Resource resource = Resource.getDefault()
                    .merge(Resource.create(Attributes.of(
                            ResourceAttributes.SERVICE_NAME, "bitovi-ai-agents",
                            ResourceAttributes.SERVICE_VERSION, "1.0.0",
                            ResourceAttributes.SERVICE_INSTANCE_ID, "agent-instance-1")));

            // Build OpenTelemetry SDK
            SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                    .addSpanProcessor(BatchSpanProcessor.builder(spanExporter)
                            .setMaxExportBatchSize(512)
                            .setScheduleDelay(Duration.ofSeconds(5))
                            .build())
                    .setResource(resource)
                    .build();

            this.openTelemetry = OpenTelemetrySdk.builder()
                    .setTracerProvider(tracerProvider)
                    .build();

            this.tracer = openTelemetry.getTracer("bitovi-ai-agents-llm", "1.0.0");

            System.out.println("OpenTelemetry LLM tracing initialized with Langfuse endpoint: " + otlpEndpoint);
        } catch (Exception e) {
            System.err.println("Failed to initialize OpenTelemetry: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Create a new LLM span for tracing model interactions
     */
    public Span startLLMSpan(String operationName, String system, String model) {
        if (tracer == null) {
            return Span.getInvalid();
        }

        return tracer.spanBuilder(operationName)
                .setSpanKind(io.opentelemetry.api.trace.SpanKind.CLIENT)
                .setAttribute(LLM_SYSTEM, system)
                .setAttribute(LLM_REQUEST_MODEL, model)
                .startSpan();
    }

    /**
     * Create a span for chat/completion operations
     */
    public Span startChatSpan(String system, String model, String conversationId) {
        Span span = startLLMSpan("llm.chat", system, model);
        if (conversationId != null) {
            span.setAttribute(LLM_CONVERSATION_ID, conversationId);
        }
        return span;
    }

    /**
     * Create a span for tool calling operations
     */
    public Span startToolCallSpan(String system, String model, List<String> toolNames) {
        Span span = startLLMSpan("llm.tool_call", system, model);
        if (toolNames != null && !toolNames.isEmpty()) {
            span.setAttribute(LLM_TOOLS, String.join(",", toolNames));
        }
        return span;
    }

    /**
     * Add prompt/input details to a span
     */
    public void addPromptAttributes(Span span, String prompt, Double temperature, Long maxTokens) {
        if (span.isRecording()) {
            span.setAttribute(LLM_REQUEST_TYPE, "chat");
            span.setAttribute(LLM_PROMPT, prompt != null ? prompt : "");
            span.setAttribute(LLM_TEMPERATURE, temperature != null ? temperature : 0.0);
            span.setAttribute(LLM_MAX_TOKENS, maxTokens != null ? maxTokens : 0L);
        }
    }

    /**
     * Add response/completion details to a span
     */
    public void addCompletionAttributes(Span span, String completion, String responseModel,
            Long promptTokens, Long completionTokens) {
        if (span.isRecording()) {
            Long totalTokens = null;
            if (promptTokens != null && completionTokens != null) {
                totalTokens = promptTokens + completionTokens;
            }

            span.setAttribute(LLM_COMPLETION, completion != null ? completion : "");
            span.setAttribute(LLM_RESPONSE_MODEL, responseModel != null ? responseModel : "");
            span.setAttribute(LLM_USAGE_PROMPT_TOKENS, promptTokens != null ? promptTokens : 0L);
            span.setAttribute(LLM_USAGE_COMPLETION_TOKENS, completionTokens != null ? completionTokens : 0L);
            span.setAttribute(LLM_USAGE_TOTAL_TOKENS, totalTokens != null ? totalTokens : 0L);
        }
    }

    /**
     * Add tool information to a span
     */
    public void addToolAttributes(Span span, List<String> toolNames, String toolResults) {
        if (span.isRecording()) {
            if (toolNames != null && !toolNames.isEmpty()) {
                span.setAttribute(LLM_TOOLS, String.join(",", toolNames));
            }
            if (toolResults != null) {
                span.setAttribute("llm.tool_results", toolResults);
            }
        }
    }

    /**
     * Complete an LLM span with success
     */
    public void finishSpanSuccess(Span span) {
        span.setStatus(StatusCode.OK);
        span.end();
    }

    /**
     * Complete an LLM span with error
     */
    public void finishSpanError(Span span, Throwable error) {
        span.setStatus(StatusCode.ERROR, error.getMessage());
        span.recordException(error);
        span.end();
    }

    /**
     * Create a scope for the span (makes it current)
     */
    public Scope withSpan(Span span) {
        return span.makeCurrent();
    }

    /**
     * Utility method to trace a lambda function
     * 
     * @throws Exception
     */
    public <T> T trace(String operationName, String system, String model, TracedOperation<T> operation)
            throws LLMProviderException {
        Span span = startLLMSpan(operationName, system, model);
        try (Scope scope = withSpan(span)) {
            T result = operation.execute(span);
            finishSpanSuccess(span);
            return result;
        } catch (Exception e) {
            finishSpanError(span, e);
            throw new LLMProviderException("Error executing traced operation: " + e.getMessage());
        }
    }

    @FunctionalInterface
    public interface TracedOperation<T> {
        T execute(Span span) throws Exception;
    }

    /**
     * Shutdown OpenTelemetry resources
     */
    public void shutdown() {
        if (openTelemetry instanceof OpenTelemetrySdk) {
            ((OpenTelemetrySdk) openTelemetry).close();
        }
    }
}
