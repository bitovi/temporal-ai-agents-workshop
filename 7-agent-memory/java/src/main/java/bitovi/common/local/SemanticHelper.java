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
import bitovi.common.local.types.SemanticMemoryAction;
import bitovi.common.local.types.SemanticMemoryRecord;
import bitovi.common.qdrant.VectorDatabaseClient;
import bitovi.workflow.types.ContextEntry;
import bitovi.workflow.types.UsageMetadata;
import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.VectorsFactory.vectors;
import static io.qdrant.client.WithPayloadSelectorFactory.enable;

import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.SearchPoints;
import io.qdrant.client.grpc.Points.UpdateResult;
import io.qdrant.client.grpc.Points.UpdateStatus;
import io.temporal.failure.ApplicationFailure;

public class SemanticHelper {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SemanticHelper.class);

    private static final String DATASTORE_SUFFIX = "semantic_memories";

    private static final Config config = new Config();

    private static final String AWS_MODEL_ID = config.getProperty("AWS_MODEL_ID");

    private final VectorDatabaseClient vdc;

    public SemanticHelper() throws InterruptedException, ExecutionException {
        vdc = new VectorDatabaseClient(DATASTORE_SUFFIX);
    }

    @SuppressWarnings("null")
    public UsageMetadata extractSemanticMemoriesImpl(String extractTemplate, String consolidateTemplate,
            String sessionId,
            List<ContextEntry> entries)
            throws InterruptedException, ExecutionException {
        // Track token usage
        UsageMetadata usage = UsageMetadata.empty();

        // Convert ContextEntry list to XML strings for LLM prompt
        List<String> contextStrings = entries.stream()
                .map(ContextEntry::toXMLString)
                .collect(Collectors.toList());

        // Truncate context
        List<String> truncatedContext = ModelUtils.truncateContextToTokenLimit(contextStrings,
                Math.floorDiv(ModelUtils.MAX_CONTEXT_TOKENS(), 2));

        // 1. Run the Extraction Step:
        // This step takes the raw chat events and extracts semantic memories from them
        List<SemanticMemoryRecord> records = extractionStep(extractTemplate, sessionId, truncatedContext, usage);
        logger.info("Extracted " + records.size() + " potential semantic memories");

        // 2. Run the Consolidation Step:
        // This step takes the extracted semantic memories, and existing
        // memories, and determines what is a new memory, what should be updated, and
        // what should be ignored.
        List<SemanticMemoryAction> actions = consolidationStep(consolidateTemplate, records, usage);
        logger.info("Consolidated into " + actions.size() + " semantic memory actions");

        // 3. Store the resulting records in the vector database
        for (SemanticMemoryAction action : actions) {
            SemanticMemoryRecord record = action.memory();
            List<Float> vectorData = BedrockEmbed.calculateEmbedding(record.fact());

            PointStruct ps = PointStruct.newBuilder()
                    .setId(id(record.id()))
                    .setVectors(vectors(vectorData))
                    .putAllPayload(record.vectorPayload())
                    .build();

            UpdateResult updateResult = vdc.upsertAsync(List.of(ps)).get();
            if (!updateResult.getStatus().equals(UpdateStatus.Completed)) {
                logger.error(
                        "Failed to insert vector with ID: " + record.id() + ", Status: " + updateResult.getStatus());
                throw new RuntimeException(
                        "Failed to insert vector with ID: " + record.id() + ", Status: " + updateResult.getStatus());
            }
        }

        return usage;
    }

    private List<SemanticMemoryRecord> extractionStep(String extractTemplate, String sessionId,
            List<String> truncatedContext, UsageMetadata usage) {

        List<ContextEntry> pastConversation = RawEventHelper.fetchRawChatEvents(sessionId);

        List<String> pastContextStrings = pastConversation.stream()
                .map(ContextEntry::toXMLString)
                .collect(Collectors.toList());

        // Format prompt with placeholders
        String systemPrompt = extractTemplate
                .replace("{pastConversation}", String.join("\n", pastContextStrings))
                .replace("{currentConversation}", String.join("\n", truncatedContext));

        // Call Bedrock with high-quality model
        ModelResponseWithUsage response = BedrockConverse.bedrockConverseWithUsage(
                systemPrompt,
                // Must start with a user message
                List.of(new ChatMessage("user", "Perform the semantic memory extraction.")),
                null, // We're going to rely on structured output parsing instead
                AWS_MODEL_ID);

        usage = usage.add(response.usage());

        String responseText = response.response();
        if (responseText == null || responseText.isEmpty()) {
            throw ApplicationFailure.newFailure("Empty response from model", "EmptyModelResponse");
        }

        List<SemanticMemoryRecord> records = new ArrayList<>();

        JSONArray jsonArray = new JSONArray(responseText);
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            records.add(SemanticMemoryRecord.fromJson(jsonObject));
        }
        return records;
    }

    private List<SemanticMemoryAction> consolidationStep(String consolidateTemplate,
            List<SemanticMemoryRecord> records,
            UsageMetadata usage) {
        List<String> memoryStrings = new ArrayList<>();

        for (SemanticMemoryRecord record : records) {
            List<SemanticMemoryRecord> relatedMemories = findRelatedMemories(record);
            memoryStrings.add(record.toRelationXML(relatedMemories));
        }

        // Format prompt with placeholders
        String systemPrompt = consolidateTemplate.replace("{memories}", String.join("\n", memoryStrings));

        // Call Bedrock with high-quality model
        ModelResponseWithUsage response = BedrockConverse.bedrockConverseWithUsage(
                systemPrompt,
                // Must start with a user message
                List.of(new ChatMessage("user", "Perform the semantic memory consolidation.")),
                null, // We're going to rely on structured output parsing instead
                AWS_MODEL_ID);

        usage = usage.add(response.usage());

        String responseText = response.response();
        if (responseText == null || responseText.isEmpty()) {
            logger.error("Empty response from model");
            throw ApplicationFailure.newFailure("Empty response from model", "EmptyModelResponse");
        }

        JSONArray jsonArray = new JSONArray(responseText);
        List<SemanticMemoryAction> actions = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject obj = jsonArray.getJSONObject(i);
            actions.add(SemanticMemoryAction.fromJson(obj));
        }

        return actions;
    }

    public List<SemanticMemoryRecord> getSemanticMemoryPayloads(List<Float> vector, int topK, float threshold) {
        try {
            List<Points.ScoredPoint> searchResponse = vdc
                    .searchAsync(SemanticMemoryRecord.vectorSearchAsync(vector, topK, threshold)).get();
            List<SemanticMemoryRecord> payloads = new ArrayList<>();

            for (Points.ScoredPoint point : searchResponse) {
                payloads.add(SemanticMemoryRecord.fromScoredPoint(point));
            }
            return payloads;
        } catch (InterruptedException | ExecutionException ex) {
            logger.error("Failed to search semantic memory embeddings: " + ex.getMessage(), ex);
            return new ArrayList<>();
        }
    }

    private List<SemanticMemoryRecord> findRelatedMemories(SemanticMemoryRecord record) {
        List<Float> vector = BedrockEmbed.calculateEmbedding(record.fact());

        // Search the vector database for related memories based on the embedding vector
        List<SemanticMemoryRecord> relatedMemories = new ArrayList<>();
        List<Points.ScoredPoint> searchResponse = null;
        try {
            searchResponse = vdc.searchAsync(SearchPoints.newBuilder()
                    .addAllVector(vector)
                    .setScoreThreshold(0.75f)
                    .setLimit(5)
                    .setWithPayload(enable(true))
                    .build()).get();
        } catch (InterruptedException | ExecutionException ex) {
            logger.error("Failed to search semantic memory embeddings: " + ex.getMessage(), ex);
        }
        if (searchResponse != null) {
            for (Points.ScoredPoint scoredPoint : searchResponse) {
                try {
                    relatedMemories.add(SemanticMemoryRecord.fromScoredPoint(scoredPoint));
                } catch (JSONException e) {
                    logger.error("Failed to parse retrieved point: " + e.getMessage(), e);
                }
            }
        }
        return relatedMemories;

    }
}
