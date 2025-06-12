package bitovi.providers;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import bitovi.records.MessageRecord;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

/**
 * Enhanced BedrockProvider with OpenTelemetry tracing for Langfuse integration
 */
public class TracedBedrockProvider extends BedrockProvider {
    private OTelLLMTracer tracer;

    public TracedBedrockProvider() {
        super();
        this.tracer = OTelLLMTracer.getInstance();
    }

    @Override
    public ArrayList<String> getModels() throws LLMProviderException {
        return tracer.trace("bedrock.list_models", "aws_bedrock", "bedrock", span -> {
            span.setAttribute("operation", "list_models");
            return super.getModels();
        });
    }

    @Override
    public String completion(String prompt) throws LLMProviderException {
        Span span = tracer.startLLMSpan("bedrock.completion", "aws_bedrock", AWS_MODEL_ID);

        try (Scope scope = tracer.withSpan(span)) {
            // Add request attributes
            tracer.addPromptAttributes(span, prompt, 0.5, null);

            // Call the actual completion
            String result = super.completion(prompt);

            // Add response attributes
            tracer.addCompletionAttributes(span, result, AWS_MODEL_ID, null, null);

            tracer.finishSpanSuccess(span);
            return result;
        } catch (Exception e) {
            tracer.finishSpanError(span, e);
            throw e;
        }
    }

    @Override
    public MessageRecord chat(ArrayList<MessageRecord> prompt) throws LLMProviderException {
        String conversationId = "conv_" + System.currentTimeMillis();
        Span span = tracer.startChatSpan("aws_bedrock", AWS_MODEL_ID, conversationId);

        try (Scope scope = tracer.withSpan(span)) {
            // Add conversation context
            String promptText = prompt.stream()
                    .map(msg -> msg.role() + ": " + msg.content())
                    .collect(Collectors.joining("\n"));

            tracer.addPromptAttributes(span, promptText, null, null);

            // Call the actual chat
            MessageRecord result = super.chat(prompt);

            // Add response attributes
            tracer.addCompletionAttributes(span, result.content(), AWS_MODEL_ID, null, null);

            tracer.finishSpanSuccess(span);
            return result;
        } catch (Exception e) {
            tracer.finishSpanError(span, e);
            throw e;
        }
    }

    @Override
    public List<List<Double>> embedding(List<String> inputs) throws LLMProviderException {
        Span span = tracer.startLLMSpan("bedrock.embedding", "aws_bedrock", AWS_MODEL_ID);

        try (Scope scope = tracer.withSpan(span)) {
            span.setAttribute("operation", "embedding");
            span.setAttribute("input_count", inputs.size());
            span.setAttribute("input_text", String.join(", ", inputs));

            // Call the actual embedding
            List<List<Double>> result = super.embedding(inputs);

            span.setAttribute("embedding_dimensions", result.isEmpty() ? 0 : result.get(0).size());
            span.setAttribute("embedding_count", result.size());

            tracer.finishSpanSuccess(span);
            return result;
        } catch (Exception e) {
            tracer.finishSpanError(span, e);
            throw e;
        }
    }
}
