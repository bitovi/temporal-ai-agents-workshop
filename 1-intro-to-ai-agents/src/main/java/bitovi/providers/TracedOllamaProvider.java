package bitovi.providers;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import bitovi.records.MessageRecord;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

/**
 * Enhanced OllamaProvider with OpenTelemetry tracing for Langfuse integration
 */
public class TracedOllamaProvider extends OllamaProvider {
    private OTelLLMTracer tracer;

    public TracedOllamaProvider() {
        super();
        this.tracer = OTelLLMTracer.getInstance();
    }

    @Override
    public ArrayList<String> getModels() throws LLMProviderException {
        return tracer.trace("ollama.list_models", "ollama", OLLAMA_MODEL_ID, span -> {
            span.setAttribute("operation", "list_models");
            span.setAttribute("ollama_host", OLLAMA_HOST);
            return super.getModels();
        });
    }

    @Override
    public String completion(String prompt) throws LLMProviderException {
        Span span = tracer.startLLMSpan("ollama.completion", "ollama", OLLAMA_MODEL_ID);

        try (Scope scope = tracer.withSpan(span)) {
            // Add request attributes
            span.setAttribute("ollama_host", OLLAMA_HOST);
            tracer.addPromptAttributes(span, prompt, null, null);

            // Call the actual completion
            String result = super.completion(prompt);

            // Add response attributes
            tracer.addCompletionAttributes(span, result, OLLAMA_MODEL_ID, null, null);

            tracer.finishSpanSuccess(span);
            return result;
        } catch (Exception e) {
            tracer.finishSpanError(span, e);
            throw e;
        }
    }

    @Override
    public MessageRecord chat(ArrayList<MessageRecord> prompt) throws LLMProviderException {
        String conversationId = "ollama_conv_" + System.currentTimeMillis();
        Span span = tracer.startChatSpan("ollama", OLLAMA_MODEL_ID, conversationId);

        try (Scope scope = tracer.withSpan(span)) {
            // Add conversation context
            span.setAttribute("ollama_host", OLLAMA_HOST);
            span.setAttribute("message_count", prompt.size());

            String promptText = prompt.stream()
                    .map(msg -> msg.role() + ": " + msg.content())
                    .collect(Collectors.joining("\n"));

            tracer.addPromptAttributes(span, promptText, null, null);

            // Call the actual chat
            MessageRecord result = super.chat(prompt);

            // Add response attributes
            tracer.addCompletionAttributes(span, result.content(), OLLAMA_MODEL_ID, null, null);

            tracer.finishSpanSuccess(span);
            return result;
        } catch (Exception e) {
            tracer.finishSpanError(span, e);
            throw e;
        }
    }

    @Override
    public List<List<Double>> embedding(List<String> inputs) throws LLMProviderException {
        Span span = tracer.startLLMSpan("ollama.embedding", "ollama", OLLAMA_MODEL_ID);

        try (Scope scope = tracer.withSpan(span)) {
            span.setAttribute("operation", "embedding");
            span.setAttribute("ollama_host", OLLAMA_HOST);
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
