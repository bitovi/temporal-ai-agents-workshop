package bitovi.providers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.json.JSONObject;

import bitovi.Config;
import bitovi.common.tools.ConsineTool.CosineToolImpl;
import bitovi.records.MessageRecord;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrock.BedrockClient;
import software.amazon.awssdk.services.bedrock.model.FoundationModelSummary;
import software.amazon.awssdk.services.bedrock.model.ListFoundationModelsResponse;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

/**
 * Enhanced BedrockProvider with OpenTelemetry tracing for Langfuse integration
 */
public class TracedBedrockProvider extends BedrockProvider {
    private OTelLLMTracer tracer;
    private final String AWS_MODEL_ID;
    private final String AWS_MODEL_ARN;
    
    public TracedBedrockProvider() {
        super();
        this.tracer = OTelLLMTracer.getInstance();
        this.AWS_MODEL_ID = Config.getProperty("AWS_MODEL_ID");
        this.AWS_MODEL_ARN = Config.getProperty("AWS_MODEL_ARN");
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
    public MessageRecord chatWithTools(ArrayList<MessageRecord> prompt, List<Tool> tools) throws LLMProviderException {
        String conversationId = "conv_" + System.currentTimeMillis();
        List<String> toolNames = tools.stream()
                .map(tool -> tool.toolSpec().name())
                .collect(Collectors.toList());
        
        Span span = tracer.startToolCallSpan("aws_bedrock", AWS_MODEL_ID, toolNames);
        
        try (Scope scope = tracer.withSpan(span)) {
            // Add conversation and tool context
            String promptText = prompt.stream()
                    .map(msg -> msg.role() + ": " + msg.content())
                    .collect(Collectors.joining("\n"));
            
            tracer.addPromptAttributes(span, promptText, null, null);
            tracer.addToolAttributes(span, toolNames, null);
            
            // Call the actual chat with tools
            MessageRecord result = super.chatWithTools(prompt, tools);
            
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
    public MessageRecord chatWithAllTools(ArrayList<MessageRecord> prompt) throws LLMProviderException {
        String conversationId = "conv_" + System.currentTimeMillis();
        
        Span span = tracer.startChatSpan("aws_bedrock", AWS_MODEL_ID, conversationId);
        span.setAttribute("operation", "chat_with_all_tools");
        
        try (Scope scope = tracer.withSpan(span)) {
            // Add conversation context
            String promptText = prompt.stream()
                    .map(msg -> msg.role() + ": " + msg.content())
                    .collect(Collectors.joining("\n"));
            
            tracer.addPromptAttributes(span, promptText, null, null);
            
            // Get available tools for tracing
            List<String> toolNames = new ArrayList<>();
            toolNames.add("calculate_cosine");
            
            // Since we can't access mcpIntegration directly, we'll just log what we can
            span.setAttribute("local_tools", "calculate_cosine");
            span.setAttribute("mcp_tools_enabled", "true");
            
            // Call the actual chat with all tools
            MessageRecord result = super.chatWithAllTools(prompt);
            
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
