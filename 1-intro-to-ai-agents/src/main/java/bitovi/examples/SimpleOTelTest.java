package bitovi.examples;

import bitovi.Config;
import bitovi.providers.OTelLLMTracer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

/**
 * Simple test to verify OpenTelemetry tracing is working with Langfuse
 */
public class SimpleOTelTest {
    
    public static void main(String[] args) {
        System.out.println("🔍 Simple OpenTelemetry + Langfuse Test");
        System.out.println("=======================================");
        
        try {
            // Check configuration
            String langfuseHost = Config.getProperty("LANGFUSE_HOST");
            if (langfuseHost == null) {
                System.err.println("❌ LANGFUSE_HOST not configured. Please set it in config.properties");
                return;
            }
            
            System.out.println("✅ Langfuse host configured: " + langfuseHost);
            
            // Initialize tracer
            OTelLLMTracer tracer = OTelLLMTracer.getInstance();
            System.out.println("✅ OpenTelemetry tracer initialized");
            
            // Create some test spans to verify tracing works
            testBasicSpan(tracer);
            testNestedSpans(tracer);
            testErrorSpan(tracer);
            
            System.out.println("\n✅ All tests completed successfully!");
            System.out.println("📊 Check your Langfuse dashboard for traces: " + langfuseHost.replaceAll("\"", ""));
            
            // Wait for traces to be exported
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
        } catch (Exception e) {
            System.err.println("❌ Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void testBasicSpan(OTelLLMTracer tracer) {
        System.out.println("\n🧪 Test 1: Basic Span");
        
        Span span = tracer.startLLMSpan("test.basic_span", "test_system", "test_model");
        
        try (Scope scope = tracer.withSpan(span)) {
            span.setAttribute("test.type", "basic");
            span.setAttribute("test.description", "Simple span test");
            
            // Simulate some work
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            tracer.addPromptAttributes(span, "Test prompt", 0.7, 100L);
            tracer.addCompletionAttributes(span, "Test response", "test_model", 10L, 20L);
            
            tracer.finishSpanSuccess(span);
            System.out.println("✅ Basic span created successfully");
            
        } catch (Exception e) {
            tracer.finishSpanError(span, e);
            throw e;
        }
    }
    
    private static void testNestedSpans(OTelLLMTracer tracer) {
        System.out.println("\n🧪 Test 2: Nested Spans");
        
        Span parentSpan = tracer.startLLMSpan("test.parent_operation", "test_system", "test_model");
        
        try (Scope parentScope = tracer.withSpan(parentSpan)) {
            parentSpan.setAttribute("operation.type", "complex");
            parentSpan.setAttribute("operation.steps", 3);
            
            // First child span
            Span childSpan1 = tracer.startLLMSpan("test.child_operation_1", "test_system", "test_model");
            try (Scope childScope1 = tracer.withSpan(childSpan1)) {
                childSpan1.setAttribute("step", 1);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                tracer.finishSpanSuccess(childSpan1);
            }
            
            // Second child span
            Span childSpan2 = tracer.startLLMSpan("test.child_operation_2", "test_system", "test_model");
            try (Scope childScope2 = tracer.withSpan(childSpan2)) {
                childSpan2.setAttribute("step", 2);
                try {
                    Thread.sleep(75);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                tracer.finishSpanSuccess(childSpan2);
            }
            
            tracer.finishSpanSuccess(parentSpan);
            System.out.println("✅ Nested spans created successfully");
            
        } catch (Exception e) {
            tracer.finishSpanError(parentSpan, e);
            throw e;
        }
    }
    
    private static void testErrorSpan(OTelLLMTracer tracer) {
        System.out.println("\n🧪 Test 3: Error Span");
        
        Span span = tracer.startLLMSpan("test.error_span", "test_system", "test_model");
        
        try (Scope scope = tracer.withSpan(span)) {
            span.setAttribute("test.type", "error_simulation");
            
            // Simulate an error
            Exception testError = new RuntimeException("Simulated error for testing");
            span.setAttribute("error.simulated", true);
            
            tracer.finishSpanError(span, testError);
            System.out.println("✅ Error span created successfully");
            
        } catch (Exception e) {
            tracer.finishSpanError(span, e);
        }
    }
}
