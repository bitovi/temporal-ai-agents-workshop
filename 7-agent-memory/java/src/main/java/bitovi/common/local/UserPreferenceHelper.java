package bitovi.common.local;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;
import static io.qdrant.client.WithPayloadSelectorFactory.enable;

import io.qdrant.client.grpc.Common.Filter;
import io.qdrant.client.grpc.Common.PointId;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.RetrievedPoint;
import io.qdrant.client.grpc.Points.ScrollPoints;
import io.qdrant.client.grpc.Points.UpdateResult;
import io.qdrant.client.grpc.Points.UpdateStatus;
import io.temporal.failure.ApplicationFailure;

public class UserPreferenceHelper {
    private static final Config config = new Config();

    private static final String AWS_MODEL_ID = config.getProperty("AWS_MODEL_ID");

    private final VectorDatabaseClient vdc;

    public UserPreferenceHelper() throws InterruptedException, ExecutionException {
        vdc = new VectorDatabaseClient();
    }

    private record UserPreferenceMemoryRecord(String context, String preference, String[] categories) {
    }

    public UsageMetadata extractUserPreferenceMemoriesImpl(String promptTemplate, String sessionId,
            List<ContextEntry> entries)
            throws InterruptedException, ExecutionException {
        System.out.println("extractUserPreferenceMemoriesImpl called with entries: " + entries);

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

        System.out.println("extractUserPreferenceMemoriesImpl System Prompt: " + systemPrompt);

        // Call Bedrock with high-quality model
        ModelResponseWithUsage response = BedrockConverse.bedrockConverseWithUsage(
                systemPrompt,
                // Must start with a user message
                List.of(new ChatMessage("user", "Perform the user-preference memory extraction.")),
                null, // We're going to rely on structured output parsing instead
                AWS_MODEL_ID);

        String responseText = response.response();
        if (responseText == null || responseText.isEmpty()) {
            throw ApplicationFailure.newFailure("Empty response from model", "EmptyModelResponse");
        }

        System.out.println("ExtractUserPreferenceMemoriesImpl Response: " + responseText);

        List<UserPreferenceMemoryRecord> records = parseExtractUserPreferenceMemoriesResult(responseText);
        // 2. Run the Consolodation Step:

        System.out.println("ExtractUserPreferenceMemoriesImpl Response: " + records);

        // TODO: Implement the consolidation step for user preference memories

        // 3. Store the resulting records in the vector database
        for (UserPreferenceMemoryRecord record : records) {
            List<Float> vector = BedrockEmbed.calculateEmbedding(record.preference());
            System.out.println("Inserting user preference memory embedding for record: "
                    + record.preference());
            insertUserPreferenceMemoryEmbedding(vector, record.preference(), record.categories());
        }

        System.out.println("ExtractUserPreferenceMemoriesImpl Usage: " + response.usage());
        return response.usage();
    }

    private List<UserPreferenceMemoryRecord> parseExtractUserPreferenceMemoriesResult(String responseText) {
        try {
            // responseText is a JSON Array of objects
            // each object has "context", "preference", and "categories" strings
            List<UserPreferenceMemoryRecord> records = new ArrayList<>();

            JSONArray jsonArray = new JSONArray(responseText);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                String context = obj.getString("context");
                String preference = obj.getString("preference");
                JSONArray categoriesArray = obj.getJSONArray("categories");
                String[] categories = new String[categoriesArray.length()];
                for (int j = 0; j < categoriesArray.length(); j++) {
                    categories[j] = categoriesArray.getString(j);
                }
                records.add(new UserPreferenceMemoryRecord(context, preference, categories));
            }

            return records;
        } catch (JSONException e) {
            System.err.println("Failed to parse user preference memory records: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private void insertUserPreferenceMemoryEmbedding(List<Float> vectorData,
            String payload,
            String[] categories)
            throws InterruptedException, ExecutionException {

        UUID uuid = java.util.UUID.randomUUID();

        @SuppressWarnings("null")
        PointId pointId = id(uuid);

        @SuppressWarnings("null")
        PointStruct ps = PointStruct.newBuilder()
                .setId(pointId)
                .setVectors(vectors(vectorData))
                .putAllPayload(
                        Map.of(
                                "payload", value(payload), "categories", value(Arrays.toString(categories)), "uuid",
                                value(uuid.toString()), "memory_type", value("user_preference_memory")))
                .build();

        @SuppressWarnings("null")
        UpdateResult updateResult = vdc.upsertAsync(List.of(ps)).get();
        if (!updateResult.getStatus().equals(UpdateStatus.Completed)) {
            throw new RuntimeException(
                    "Failed to insert vector with ID: " + pointId + ", Status: " + updateResult.getStatus());
        }
    }

    @SuppressWarnings("null")
    public List<String> getUserPreferenceMemoryPayloads() {
        @SuppressWarnings("null")
        Points.ScrollResponse scrollResponse = null;
        try {
            scrollResponse = vdc.scrollAsync(ScrollPoints.newBuilder()
                    .setFilter(
                            Filter.newBuilder()
                                    .addAllShould(
                                            List.of(matchKeyword("memory_type", "user_preference_memory")))
                                    .build())
                    .setWithPayload(enable(true))
                    .build()).get();
        } catch (InterruptedException | ExecutionException ex) {
            System.err.println("Failed to search user preference memory embeddings: " + ex.getMessage());
        }

        List<String> payloads = new ArrayList<>();

        if (scrollResponse == null) {
            return payloads;
        }

        var points = scrollResponse.getResultList();

        for (RetrievedPoint point : points) {
            String payload = point.getPayloadMap().get("payload").getStringValue();
            payloads.add(payload);
        }
        return payloads;
    }
}
