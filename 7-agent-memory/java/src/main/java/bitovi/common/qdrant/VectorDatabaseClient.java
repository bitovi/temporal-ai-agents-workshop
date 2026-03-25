package bitovi.common.qdrant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import com.google.common.util.concurrent.ListenableFuture;

import bitovi.common.Config;
import static io.qdrant.client.PointIdFactory.id;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;
import io.qdrant.client.grpc.Collections.CollectionOperationResponse;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.PayloadSchemaType;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Common.PointId;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.RetrievedPoint;
import io.qdrant.client.grpc.Points.ScrollPoints;
import io.qdrant.client.grpc.Points.ScrollResponse;
import io.qdrant.client.grpc.Points.SearchPoints;
import io.qdrant.client.grpc.Points.UpdateResult;
import io.qdrant.client.grpc.Points.UpdateStatus;

public class VectorDatabaseClient {
    /*
     * Amazon Titan Embeddings G1 - Text Floating-point 1536
     * Amazon Titan Text Embeddings V2 Floating-point, binary 256, 512, 1024
     * Cohere Embed (English) Floating-point, binary 1024
     * Cohere Embed (Multilingual) Floating-point, binary 1024
     * 
     * Ollama nomic-embed-text:latest 4096
     */
    private static final Integer COLLECTION_VECTOR_SIZE = 1024; // titan-embed-text-v2:0

    private static final Config config = new Config();
    private static final String USER_ID = config.getProperty("USER_ID");
    private static final String QDRANT_HOST = config.getProperty("QDRANT_HOST");
    private static final Integer QDRANT_PORT_GRPC = config.getIntegerProperty("QDRANT_PORT_GRPC");
    private final QdrantClient client;

    private final String collectionName = USER_ID;

    @SuppressWarnings("null")
    public VectorDatabaseClient() throws InterruptedException, ExecutionException {
        QdrantGrpcClient grpc = QdrantGrpcClient.newBuilder(QDRANT_HOST, QDRANT_PORT_GRPC, false)
                .build();
        QdrantClient temp = new QdrantClient(grpc);

        // Initialize the collection name based on the user ID
        List<String> existing = temp.listCollectionsAsync().get();
        if (!existing.contains(USER_ID)) {
            CollectionOperationResponse result = temp.createCollectionAsync(USER_ID,
                    VectorParams.newBuilder()
                            .setDistance(Distance.Cosine)
                            .setSize(COLLECTION_VECTOR_SIZE)
                            .build())
                    .get();
            if (result.getResult()) {
                System.out.println("Collection '" + USER_ID + "' created successfully.");
            } else {
                throw new RuntimeException("Failed to create collection '" + USER_ID + "'.");
            }

            temp.createPayloadIndexAsync(
                    USER_ID,
                    "created_at",
                    PayloadSchemaType.Datetime,
                    null,
                    true,
                    null,
                    null);

            temp.createPayloadIndexAsync(
                    USER_ID,
                    "updated_at",
                    PayloadSchemaType.Datetime,
                    null,
                    true,
                    null,
                    null);
        }

        // Once we've gotten here, we have ensured that the collection exists
        this.client = temp;
    }

    @SuppressWarnings("null")
    public ListenableFuture<UpdateResult> upsertAsync(List<PointStruct> points) {
        return client.upsertAsync(collectionName, points);
    }

    @SuppressWarnings("null")
    public ListenableFuture<ScrollResponse> scrollAsync(ScrollPoints request) {
        var builder = request.toBuilder();
        builder.setCollectionName(collectionName);
        return client.scrollAsync(builder.build());
    }

    @SuppressWarnings("null")
    public ListenableFuture<List<Points.ScoredPoint>> searchAsync(SearchPoints request) {
        var builder = request.toBuilder();
        builder.setCollectionName(collectionName);
        return client.searchAsync(builder.build());
    }

    public String[] searchVectorDatabase(List<Float> vector, Integer limit,
            Float scoreThreshold)
            throws InterruptedException, ExecutionException {
        @SuppressWarnings("null")
        List<Points.ScoredPoint> points = client
                .searchAsync(
                        SearchPoints.newBuilder()
                                .setCollectionName(collectionName)
                                .addAllVector(vector)
                                .setScoreThreshold(scoreThreshold)
                                .setLimit(limit)
                                .build())
                .get();

        if (points == null || points.isEmpty()) {
            return new String[0];
        }

        String[] uuids = new String[points.size()];
        for (int i = 0; i < points.size(); i++) {
            var payload = points.get(i);
            uuids[i] = payload.getId().getUuid();
        }
        return uuids;
    }

    @SuppressWarnings("null")
    public ArrayList<String> getPayloadsByIds(String[] uuids)
            throws InterruptedException, ExecutionException {

        List<PointId> pointIds = new ArrayList<>();
        for (String uuid : uuids) {
            pointIds.add(id(UUID.fromString(uuid)));
        }

        ArrayList<String> payloads = new ArrayList<>();
        @SuppressWarnings("null")
        List<RetrievedPoint> points = client.retrieveAsync(collectionName, pointIds, null).get();
        for (RetrievedPoint point : points) {
            String payload = point.getPayloadMap().get("payload").getStringValue();
            payloads.add(payload);
        }

        return payloads;
    }

    public void insertEmbedding(UUID uuid, List<Float> vectorData, String payload,
            String source)
            throws InterruptedException, ExecutionException {
        @SuppressWarnings("null")
        PointId pointId = id(uuid);
        @SuppressWarnings("null")
        PointStruct ps = PointStruct.newBuilder()
                .setId(pointId)
                .setVectors(vectors(vectorData))
                .putAllPayload(
                        Map.of(
                                "payload", value(payload), "source", value(source), "uuid", value(uuid.toString())))
                .build();

        @SuppressWarnings("null")
        UpdateResult updateResult = client.upsertAsync(collectionName, List.of(ps)).get();
        if (!updateResult.getStatus().equals(UpdateStatus.Completed)) {
            throw new RuntimeException(
                    "Failed to insert vector with ID: " + pointId + ", Status: " + updateResult.getStatus());
        }
    }
}