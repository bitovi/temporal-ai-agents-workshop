package bitovi.common.qdrant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import bitovi.common.Config;
import static io.qdrant.client.PointIdFactory.id;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;
import io.qdrant.client.grpc.Collections.CollectionOperationResponse;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.PointId;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.RetrievedPoint;
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

    private final QdrantClient client;

    private final String collectionName;

    @SuppressWarnings("null")
    public VectorDatabaseClient(String collectionName) throws InterruptedException, ExecutionException {
        Config config = new Config();
        String qdrantHost = config.getProperty("QDRANT_HOST");
        Integer qdrantPort = config.getIntegerProperty("QDRANT_PORT_GRPC");

        // Initialize the Qdrant client with the configuration
        @SuppressWarnings("null")
        QdrantGrpcClient grpc = QdrantGrpcClient.newBuilder(qdrantHost, qdrantPort, false)
                .build();
        this.client = new QdrantClient(grpc);
        this.collectionName = collectionName;
    }

    private Boolean hasCollection() throws InterruptedException, ExecutionException {
        List<String> existing = client.listCollectionsAsync().get();
        return existing.contains(collectionName);
    }

    public void createCollection() throws InterruptedException, ExecutionException {
        if (hasCollection()) {
            System.out.println("Collection '" + collectionName + "' already exists.");
            return;
        }

        @SuppressWarnings("null")
        CollectionOperationResponse result = client.createCollectionAsync(collectionName,
                VectorParams.newBuilder()
                        .setDistance(Distance.Cosine)
                        .setSize(COLLECTION_VECTOR_SIZE)
                        .build())
                .get();
        if (result.getResult()) {
            System.out.println("Collection '" + collectionName + "' created successfully.");
        } else {
            System.err.println("Failed to create collection '" + collectionName + "'.");
        }
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
            return null;
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
