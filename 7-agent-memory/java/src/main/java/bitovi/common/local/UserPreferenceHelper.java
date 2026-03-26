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
import bitovi.common.local.types.UserPreferenceMemoryActions;
import bitovi.common.local.types.UserPreferenceMemoryRecord;
import bitovi.common.qdrant.VectorDatabaseClient;
import bitovi.workflow.types.ContextEntry;
import bitovi.workflow.types.UsageMetadata;
import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.VectorsFactory.vectors;
import static io.qdrant.client.WithPayloadSelectorFactory.enable;

import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.RetrievedPoint;
import io.qdrant.client.grpc.Points.SearchPoints;
import io.qdrant.client.grpc.Points.UpdateResult;
import io.qdrant.client.grpc.Points.UpdateStatus;
import io.temporal.failure.ApplicationFailure;

public class UserPreferenceHelper {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(UserPreferenceHelper.class);

    private static final String DATASTORE_SUFFIX = "user_pref_memories";

    private static final Config config = new Config();

    private static final String AWS_MODEL_ID = config.getProperty("AWS_MODEL_ID");

    private final VectorDatabaseClient vdc;

    public UserPreferenceHelper() throws InterruptedException, ExecutionException {
        vdc = new VectorDatabaseClient(DATASTORE_SUFFIX);
    }

    public void close() {
        vdc.close();
    }

    @SuppressWarnings("null")
    public UsageMetadata extractUserPreferenceMemoriesImpl(String extractTemplate, String consolidateTemplate,
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

        // 1. Run the Extraction Step
        // This step takes the raw chat events and extracts user preference memories
        // from them
        List<UserPreferenceMemoryRecord> records = extractionStep(extractTemplate, sessionId, truncatedContext, usage);

        logger.info("Extracted " + records.size() + " potential user preference memories");

        // 2. Run the Consolidation Step
        // This step takes the extracted user preference memories, and existing
        // memories, and determines what is a new memory, what should be updated, and
        // what should be ignored.
        List<UserPreferenceMemoryActions> actions = consolidationStep(consolidateTemplate, records, usage);
        logger.info("Consolidated into " + actions.size() + " user preference memory actions");

        // 3. Store the resulting records in the vector database
        // This step takes the resulting actions and update the database
        for (UserPreferenceMemoryActions action : actions) {
            UserPreferenceMemoryRecord record = action.memory();
            List<Float> vectorData = BedrockEmbed.calculateEmbedding(record.context());

            PointStruct ps = PointStruct.newBuilder()
                    .setId(id(record.id()))
                    .setVectors(vectors(vectorData))
                    .putAllPayload(record.vectorPayload())
                    .build();

            UpdateResult updateResult = vdc.upsertAsync(List.of(ps)).get();
            if (!updateResult.getStatus().equals(UpdateStatus.Completed)) {
                throw new RuntimeException(
                        "Failed to insert vector with ID: " + record.id() + ", Status: " + updateResult.getStatus());
            }
        }

        return usage;
    }

    private List<UserPreferenceMemoryRecord> extractionStep(String promptTemplate, String sessionId,
            List<String> truncatedContext, UsageMetadata usage) {
        // 1. Run the Extraction Step:
        List<ContextEntry> pastConversation = RawEventHelper.fetchRawChatEvents(sessionId);

        List<String> pastContextStrings = pastConversation.stream()
                .map(ContextEntry::toXMLString)
                .collect(Collectors.toList());

        // Format prompt with placeholders
        String systemPrompt = promptTemplate
                .replace("{pastConversation}", String.join("\n", pastContextStrings))
                .replace("{currentConversation}", String.join("\n", truncatedContext));

        // Call Bedrock with high-quality model
        ModelResponseWithUsage response = BedrockConverse.bedrockConverseWithUsage(
                systemPrompt,
                // Must start with a user message
                List.of(new ChatMessage("user", "Perform the user-preference memory extraction.")),
                null, // We're going to rely on structured output parsing instead
                AWS_MODEL_ID);

        usage = usage.add(response.usage());

        String responseText = response.response();
        if (responseText == null || responseText.isEmpty()) {
            throw ApplicationFailure.newFailure("Empty response from model", "EmptyModelResponse");
        }

        List<UserPreferenceMemoryRecord> records = new ArrayList<>();
        JSONArray jsonArray = new JSONArray(responseText);
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject obj = jsonArray.getJSONObject(i);
            records.add(UserPreferenceMemoryRecord.fromJson(obj));
        }
        return records;
    }

    private List<UserPreferenceMemoryActions> consolidationStep(String consolidateTemplate,
            List<UserPreferenceMemoryRecord> records,
            UsageMetadata usage) {

        List<String> memoryStrings = new ArrayList<>();

        for (UserPreferenceMemoryRecord record : records) {
            List<UserPreferenceMemoryRecord> relatedMemories = findRelatedMemories(record);
            memoryStrings.add(record.toRelationXML(relatedMemories));
        }

        // Format prompt with placeholders
        String systemPrompt = consolidateTemplate.replace("{memories}", String.join("\n", memoryStrings));

        // Call Bedrock with high-quality model
        ModelResponseWithUsage response = BedrockConverse.bedrockConverseWithUsage(
                systemPrompt,
                // Must start with a user message
                List.of(new ChatMessage("user", "Perform the user-preference memory consolidation.")),
                null, // We're going to rely on structured output parsing instead
                AWS_MODEL_ID);

        usage = usage.add(response.usage());

        String responseText = response.response();
        if (responseText == null || responseText.isEmpty()) {
            throw ApplicationFailure.newFailure("Empty response from model", "EmptyModelResponse");
        }

        JSONArray jsonArray = new JSONArray(responseText);
        List<UserPreferenceMemoryActions> actions = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject obj = jsonArray.getJSONObject(i);
            actions.add(UserPreferenceMemoryActions.fromJson(obj));
        }

        return actions;
    }

    public List<UserPreferenceMemoryRecord> getUserPreferenceMemoryPayloads() {
        Points.ScrollResponse scrollResponse = null;
        try {
            scrollResponse = vdc.scrollAsync(UserPreferenceMemoryRecord.vectorScrollSearch()).get();
        } catch (InterruptedException | ExecutionException ex) {
            logger.error("Failed to search user preference memory embeddings: " + ex.getMessage(), ex);
        }

        if (scrollResponse == null) {
            return new ArrayList<>();
        }

        List<RetrievedPoint> points = scrollResponse.getResultList();
        List<UserPreferenceMemoryRecord> payloads = new ArrayList<>();

        for (RetrievedPoint point : points) {
            try {
                payloads.add(UserPreferenceMemoryRecord.fromRetrievedPoint(point));
            } catch (JSONException e) {
                logger.error("Failed to parse retrieved point: " + e.getMessage(), e);
            }
        }
        return payloads;
    }

    private List<UserPreferenceMemoryRecord> findRelatedMemories(UserPreferenceMemoryRecord record) {
        // Here we should find related memories based on the memory categories
        List<Float> vector = BedrockEmbed.calculateEmbedding(String.join(" ", record.categories()));

        // Search the vector database for related memories based on the embedding vector
        List<UserPreferenceMemoryRecord> relatedMemories = new ArrayList<>();
        List<Points.ScoredPoint> searchResponse = null;
        try {
            searchResponse = vdc.searchAsync(SearchPoints.newBuilder()
                    .addAllVector(vector)
                    .setScoreThreshold(0.75f)
                    .setLimit(5)
                    .setWithPayload(enable(true))
                    .build()).get();
        } catch (InterruptedException | ExecutionException ex) {
            logger.error("Failed to search user preference memory embeddings: " + ex.getMessage(), ex);
        }
        if (searchResponse != null) {
            for (Points.ScoredPoint scoredPoint : searchResponse) {
                try {
                    relatedMemories.add(UserPreferenceMemoryRecord.fromScoredPoint(scoredPoint));
                } catch (JSONException e) {
                    logger.error("Failed to parse retrieved point: " + e.getMessage(), e);
                }
            }
        }
        return relatedMemories;

    }
}
