package bitovi.common.local;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import bitovi.common.aws.BedrockEmbed;
import bitovi.common.qdrant.VectorDatabaseClient;
import bitovi.workflow.types.ContextEntry;
import bitovi.workflow.types.ContextEntryType;
import static io.qdrant.client.ConditionFactory.matchKeyword;
import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;
import static io.qdrant.client.WithPayloadSelectorFactory.enable;

import io.qdrant.client.grpc.Common.Filter;
import io.qdrant.client.grpc.Common.PointId;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.Direction;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.RetrievedPoint;
import io.qdrant.client.grpc.Points.ScrollPoints;
import io.qdrant.client.grpc.Points.UpdateResult;
import io.qdrant.client.grpc.Points.UpdateStatus;
import software.amazon.awssdk.services.bedrockagentcore.model.Role;

public class RawEventHelper {
    /**
     * This function should persist the session events for the given sessionId and
     * entries into
     * Qdrant so they can be retrieved later for memory-extraction reasoning.
     * 
     * @param sessionId The ID of the session for which the events are being
     *                  persisted.
     * @param entries   The list of context entries representing the session events.
     * @throws InterruptedException
     * @throws ExecutionException
     */
    public static void persistSessionEventsImpl(String sessionId, List<ContextEntry> entries)
            throws InterruptedException, ExecutionException {
        for (ContextEntry record : entries) {
            List<Float> vector = BedrockEmbed.calculateEmbedding(record.content());
            insertRawChatEventEmbedding(vector, record.content(), record.role().toString(), sessionId);
        }
    }

    public static void insertRawChatEventEmbedding(List<Float> vectorData, String payload, String role,
            String sessionId)
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
                                "payload", value(payload),
                                "role", value(role),
                                "uuid", value(uuid.toString()),
                                "memory_type", value("raw"),
                                "session_id", value(sessionId),
                                "created_at", value(Instant.now().toString()),
                                "updated_at", value(Instant.now().toString())))
                .build();

        VectorDatabaseClient vdc = new VectorDatabaseClient();
        UpdateResult updateResult = vdc.upsertAsync(List.of(ps)).get();
        if (!updateResult.getStatus().equals(UpdateStatus.Completed)) {
            throw new RuntimeException(
                    "Failed to insert vector with ID: " + pointId + ", Status: " + updateResult.getStatus());
        }
    }

    @SuppressWarnings("null")
    public static List<ContextEntry> fetchRawChatEvents(String sessionId) {
        @SuppressWarnings("null")
        Points.ScrollResponse scrollResponse = null;
        try {
            // Query to return X number of results, ordered by date descending
            VectorDatabaseClient vdc = new VectorDatabaseClient();
            scrollResponse = vdc.scrollAsync(ScrollPoints.newBuilder()
                    .setFilter(
                            Filter.newBuilder()
                                    .addAllShould(
                                            List.of(matchKeyword("memory_type", "raw")))
                                    .build())
                    .setWithPayload(enable(true))
                    .setLimit(20)
                    .setOrderBy(Points.OrderBy.newBuilder().setKey("created_at").setDirection(Direction.Desc))
                    .build()).get();
        } catch (InterruptedException | ExecutionException ex) {
            System.err.println("Failed to raw message embeddings: " + ex.getMessage());
        }
        
        List<ContextEntry> payloads = new ArrayList<>();

        if (scrollResponse == null) {
            return payloads;
        }

        var points = scrollResponse.getResultList();

        for (RetrievedPoint point : points) {
            Map<String, Value> payloadMap = point.getPayloadMap();
            String payload = payloadMap.get("payload").getStringValue();
            Role role = Role.fromValue(payloadMap.get("role").getStringValue());
            Instant createdAt = Instant.parse(payloadMap.get("created_at").getStringValue());

            if (role.equals(Role.USER)) {
                payloads.add(ContextEntry.fromUser(payload, createdAt));
            } else {
                payloads.add(ContextEntry.fromAnswer(payload, createdAt));
            }
        }
        return payloads;
    }
}
