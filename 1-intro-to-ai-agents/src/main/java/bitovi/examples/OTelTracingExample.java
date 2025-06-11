package bitovi.examples;

import java.util.ArrayList;
import java.util.List;

import bitovi.Config;
import bitovi.providers.TracedBedrockProvider;
import bitovi.providers.TracedOllamaProvider;
import bitovi.providers.LangfuseProvider;
import bitovi.providers.OTelLLMTracer;
import bitovi.providers.LLMProviderException;
import bitovi.records.MessageRecord;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

/**
 * Example demonstrating OpenTelemetry tracing with Langfuse integration
 * for AWS Bedrock and Ollama LLM providers
 */
public class OTelTracingExample {

    public static void main(String[] args) {
        System.out.println("OpenTelemetry + Langfuse LLM Tracing Example");
        System.out.println("============================================");

        try {
            // Verify configuration
            String langfuseHost = Config.getProperty("LANGFUSE_HOST");
            if (langfuseHost == null) {
                System.err.println("LANGFUSE_HOST not configured. Please set it in config.properties");
                return;
            }

            System.out.println("Langfuse endpoint: " + langfuseHost + "/api/public/otel");

            // Initialize providers with tracing
            System.out.println("\nInitializing traced providers...");
            TracedBedrockProvider bedrockProvider = new TracedBedrockProvider();
            TracedOllamaProvider ollamaProvider = new TracedOllamaProvider();
            LangfuseProvider langfuseProvider = new LangfuseProvider();
            OTelLLMTracer tracer = OTelLLMTracer.getInstance();

            // Example 1: Simple completion with Bedrock
            System.out.println("\n--- Example 1: Bedrock Completion ---");
            testBedrockCompletion(bedrockProvider);

            // Example 2: Chat conversation with Bedrock
            System.out.println("\n--- Example 2: Bedrock Chat ---");
            testBedrockChat(bedrockProvider);

            // Example 3: Tool calling with Bedrock
            System.out.println("\n--- Example 3: Bedrock Tool Calling ---");
            testBedrockToolCalling(bedrockProvider);

            // Example 4: Ollama completion (if available)
            System.out.println("\n--- Example 4: Ollama Completion ---");
            testOllamaCompletion(ollamaProvider);

            // Example 5: Custom tracing with span attributes
            System.out.println("\n--- Example 5: Custom Tracing ---");
            testCustomTracing(tracer, bedrockProvider);

            // Example 6: Error handling with tracing
            System.out.println("\n--- Example 6: Error Handling ---");
            testErrorHandling(bedrockProvider);

            System.out.println("\nAll examples completed!");
            System.out.println("Check your Langfuse dashboard for traces: " + langfuseHost);

            // Allow time for traces to be sent
            Thread.sleep(5000);

        } catch (Exception e) {
            System.err.println("Error running examples: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testBedrockCompletion(TracedBedrockProvider provider) {
        try {
            String prompt = "What is artificial intelligence in one sentence?";
            String result = provider.completion(prompt);
            System.out.println("Prompt: " + prompt);
            System.out.println("Response: " + result);
        } catch (Exception e) {
            System.err.println("Bedrock completion failed: " + e.getMessage());
        }
    }

    private static void testBedrockChat(TracedBedrockProvider provider) {
        try {
            ArrayList<MessageRecord> messages = new ArrayList<>();
            messages.add(new MessageRecord("user", "Hello, how are you?"));

            MessageRecord response = provider.chat(messages);
            System.out.println("User: Hello, how are you?");
            System.out.println("Assistant: " + response.content());
        } catch (Exception e) {
            System.err.println("Bedrock chat failed: " + e.getMessage());
        }
    }

    private static void testBedrockToolCalling(TracedBedrockProvider provider) {
        try {
            ArrayList<MessageRecord> messages = new ArrayList<>();
            messages.add(new MessageRecord("user", "Calculate the cosine of 1.57 radians"));

            MessageRecord response = provider.chatWithAllTools(messages);
            System.out.println("User: Calculate the cosine of 1.57 radians");
            System.out.println("Assistant: " + response.content());
        } catch (Exception e) {
            System.err.println("Bedrock tool calling failed: " + e.getMessage());
        }
    }

    private static void testOllamaCompletion(TracedOllamaProvider provider) {
        try {
            String prompt = "Explain machine learning in simple terms.";
            String result = provider.completion(prompt);
            System.out.println("Prompt: " + prompt);
            System.out.println("Response: " + result);
        } catch (Exception e) {
            System.err
                    .println("Ollama completion failed (this is expected if Ollama is not running): " + e.getMessage());
        }
    }    private static void testCustomTracing(OTelLLMTracer tracer, TracedBedrockProvider provider) {
        // Example of custom tracing with additional context
        Span parentSpan = tracer.startLLMSpan("custom.ai_conversation", "bedrock", "claude");
        
        try (Scope scope = tracer.withSpan(parentSpan)) {
            parentSpan.setAttribute("user_id", "user_123");
            parentSpan.setAttribute("session_id", "session_456");
            parentSpan.setAttribute("conversation_type", "customer_support");
            
            ArrayList<MessageRecord> messages = new ArrayList<>();
            messages.add(new MessageRecord("user", "I need help with my account"));
            
            try {
                MessageRecord response = provider.chat(messages);
                
                parentSpan.setAttribute("issue_category", "account_support");
                parentSpan.setAttribute("response_sentiment", "helpful");
                
                System.out.println("Custom traced conversation completed");
                System.out.println("Response: " + response.content());
                
                tracer.finishSpanSuccess(parentSpan);
            } catch (LLMProviderException e) {
                System.out.println("Custom tracing failed: " + e.getMessage());
                tracer.finishSpanError(parentSpan, e);
            }
        } catch (Exception e) {
            tracer.finishSpanError(parentSpan, e);
        }
    }    private static void testErrorHandling(TracedBedrockProvider provider) {
        // Example of error handling with tracing
        OTelLLMTracer tracer = OTelLLMTracer.getInstance();
        Span span = tracer.startLLMSpan("error.test", "bedrock", "test");
        
        try (Scope scope = tracer.withSpan(span)) {
            // This will likely cause an error due to empty prompt
            ArrayList<MessageRecord> messages = new ArrayList<>();
            messages.add(new MessageRecord("user", ""));
            
            try {
                MessageRecord response = provider.chat(messages);
                System.out.println("Unexpected success: " + response.content());
                tracer.finishSpanSuccess(span);
            } catch (LLMProviderException e) {
                System.out.println("Expected error caught and traced: " + e.getMessage());
                tracer.finishSpanError(span, e);
            }
        } catch (Exception e) {
            System.out.println("Error in test: " + e.getMessage());
            tracer.finishSpanError(span, e);
        }
    }
}
