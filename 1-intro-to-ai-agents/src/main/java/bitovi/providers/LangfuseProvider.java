package bitovi.providers;

import com.langfuse.client.LangfuseClient;
import com.langfuse.client.resources.prompts.types.PromptMetaListResponse;

import bitovi.Config;

public class LangfuseProvider {
    private LangfuseClient langfuseClient;
    private OTelLLMTracer otelTracer;

    public LangfuseProvider() {
        String LANGFUSE_SECRET_KEY = Config.getProperty("LANGFUSE_SECRET_KEY");
        String LANGFUSE_PUBLIC_KEY = Config.getProperty("LANGFUSE_PUBLIC_KEY");
        String LANGFUSE_HOST = Config.getProperty("LANGFUSE_HOST");

        this.langfuseClient = LangfuseClient.builder()
                .url(LANGFUSE_HOST)
                .credentials(LANGFUSE_PUBLIC_KEY, LANGFUSE_SECRET_KEY)
                .build();
        
        // Initialize OpenTelemetry tracing
        this.otelTracer = OTelLLMTracer.getInstance();
    }

    public LangfuseClient getLangfuseClient() {
        return langfuseClient;
    }

    public PromptMetaListResponse getPromptsList() {
        return langfuseClient.prompts().list();
    }

    public OTelLLMTracer getTracer() {
        return otelTracer;
    }

    public void shutdown() {
        if (otelTracer != null) {
            otelTracer.shutdown();
        }
    }
}
