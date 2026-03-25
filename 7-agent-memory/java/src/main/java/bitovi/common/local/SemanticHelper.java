package bitovi.common.local;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import bitovi.common.Config;
import bitovi.common.ModelUtils;
import bitovi.common.aws.BedrockConverse;
import bitovi.common.aws.BedrockConverse.ChatMessage;
import bitovi.common.aws.BedrockConverse.ModelResponseWithUsage;
import bitovi.common.aws.BedrockEmbed;
import bitovi.common.qdrant.VectorDatabaseClient;
import bitovi.workflow.types.ContextEntry;
import bitovi.workflow.types.UsageMetadata;
import static io.qdrant.client.ConditionFactory.matchKeyword;
import static io.qdrant.client.WithPayloadSelectorFactory.enable;

import io.qdrant.client.grpc.Common.Filter;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.SearchPoints;
import io.temporal.failure.ApplicationFailure;

public class SemanticHelper {
    private static final Config config = new Config();

    private static final String AWS_MODEL_ID = config.getProperty("AWS_MODEL_ID");

    private final VectorDatabaseClient vdc;

    public SemanticHelper() throws InterruptedException, ExecutionException {
        vdc = new VectorDatabaseClient();
    }

    public record SemanticMemoryRecord(String fact) {

    }

    public UsageMetadata extractSemanticMemoriesImpl(String promptTemplate, String sessionId,
            List<ContextEntry> entries)
            throws InterruptedException, ExecutionException {
        System.out.println("extractSemanticMemoriesImpl called with entries: " + entries);

        // Convert ContextEntry list to XML strings for LLM prompt
        List<String> contextStrings = entries.stream()
                .map(ContextEntry::toXMLString)
                .collect(Collectors.toList());

        // Truncate context
        List<String> truncatedContext = ModelUtils.truncateContextToTokenLimit(contextStrings,
                Math.floorDiv(ModelUtils.MAX_CONTEXT_TOKENS(), 2));

        // 1. Run the Extraction Step:
        List<ContextEntry> pastConversation = RawEventHelper.fetchRawChatEvents(sessionId);

        List<String> pastContextStrings = pastConversation.stream()
                .map(ContextEntry::toXMLString)
                .collect(Collectors.toList());

        // Format prompt with placeholders
        String systemPrompt = promptTemplate
                .replace("{pastConversation}", String.join("\n", pastContextStrings))
                .replace("{currentConversation}", String.join("\n", truncatedContext));

        System.out.println("extractSemanticMemoriesImpl System Prompt: " + systemPrompt);

        // Call Bedrock with high-quality model
        ModelResponseWithUsage response = BedrockConverse.bedrockConverseWithUsage(
                systemPrompt,
                // Must start with a user message
                List.of(new ChatMessage("user", "Perform the semantic memory extraction.")),
                null, // We're going to rely on structured output parsing instead
                AWS_MODEL_ID);

        String responseText = response.response();
        if (responseText == null || responseText.isEmpty()) {
            throw ApplicationFailure.newFailure("Empty response from model", "EmptyModelResponse");
        }

        System.out.println("ExtractSemanticMemoriesImpl Response: " + responseText);

        List<SemanticMemoryRecord> records = parseExtractSemanticMemoriesResult(responseText);
        // 2. Run the Consolodation Step:

        System.out.println("ExtractSemanticMemoriesImpl Response: " + records);

        // TODO: Implement the consolidation step for semantic memories

        // 3. Store the resulting records in the vector database
        for (SemanticMemoryRecord record : records) {
            List<Float> vector = BedrockEmbed.calculateEmbedding(record.fact());
            System.out.println("Inserting semantic memory embedding for record: "
                    + record.fact());
            insertSemanticMemoryEmbedding(vector, record.fact());
        }

        System.out.println("ExtractSemanticMemoriesImpl Usage: " + response.usage());
        return response.usage();
    }

    private List<SemanticMemoryRecord> parseExtractSemanticMemoriesResult(String responseText) {
        try {
            // responseText is a JSON Array that contains objects with a 'fact' field
            // The 'fact' contains a string representing the semantic memory
            List<SemanticMemoryRecord> records = new ArrayList<>();

            JSONArray jsonArray = new JSONArray(responseText);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String fact = jsonObject.getString("fact");
                records.add(new SemanticMemoryRecord(fact));
            }
            return records;
        } catch (JSONException e) {
            System.err.println("Failed to parse semantic memory records: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @SuppressWarnings("null")
    public List<String> getSemanticMemoryPayloads(List<Float> vector, int topK, float threshold) {
        @SuppressWarnings("null")
        List<Points.ScoredPoint> searchResponse = null;
        try {
            searchResponse = vdc.searchAsync(SearchPoints.newBuilder()
                    .addAllVector(vector)
                    .setLimit(topK)
                    .setFilter(
                            Filter.newBuilder()
                                    .addAllShould(
                                            List.of(matchKeyword("memory_type", "semantic_memory")))
                                    .build())
                    .setWithPayload(enable(true))
                    .build()).get();
        } catch (InterruptedException | ExecutionException ex) {
            System.err.println("Failed to search semantic memory embeddings: " + ex.getMessage());
        }

        List<String> payloads = new ArrayList<>();

        if (searchResponse == null) {
            return payloads;
        }

        for (Points.ScoredPoint point : searchResponse) {
            String payload = point.getPayloadMap().get("payload").getStringValue();
            payloads.add(payload);
        }
        return payloads;
    }

    public void insertSemanticMemoryEmbedding(List<Float> vectorData,
            String fact)
            throws InterruptedException, ExecutionException {
        System.out.println("Inserting semantic memory embedding for fact: " + fact);
    }
}
