package bitovi.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
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

import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;

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
    private static final String COLLECTION_NAME = "rag-knowledge-base";
    private static QdrantClient instance;

    public static QdrantClient getQdrantClient() throws InterruptedException, ExecutionException {
        if (instance == null) {
            new VectorDatabaseClient();
        }
        return instance;
    }

    private VectorDatabaseClient() throws InterruptedException, ExecutionException {
        Config config = new Config();
        String qdrantHost = config.getProperty("QDRANT_HOST");
        Integer qdrantPort = config.getIntegerProperty("QDRANT_PORT");
        String qdrantApiKey = config.getProperty("QDRANT_SERVICE_API_KEY");

        // Initialize the Qdrant client with the configuration
        QdrantGrpcClient grpc = QdrantGrpcClient.newBuilder(qdrantHost, qdrantPort, false).withApiKey(qdrantApiKey)
                .build();
        VectorDatabaseClient.instance = new QdrantClient(grpc);

        // Create the default collection if it does not exist
        if (!hasCollection(COLLECTION_NAME)) {
            createCollection(COLLECTION_NAME);
        }
    }

    private static Boolean hasCollection(String collectionName) throws InterruptedException, ExecutionException {
        QdrantClient client = getQdrantClient();
        List<String> existing = client.listCollectionsAsync().get();
        if (existing.contains(collectionName)) {
            return true;
        }

        return false;
    }

    private static void createCollection(String collectionName) throws InterruptedException, ExecutionException {
        QdrantClient client = getQdrantClient();
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

    public static String[] searchVectorDatabase(List<Float> vector, Integer limit, Float scoreThreshold)
            throws InterruptedException, ExecutionException {
        QdrantClient client = getQdrantClient();
        List<Points.ScoredPoint> points = client
                .searchAsync(
                        SearchPoints.newBuilder()
                                .setCollectionName(COLLECTION_NAME)
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

    public static ArrayList<String> getPayloadsByIds(String[] uuids) throws InterruptedException, ExecutionException {
        QdrantClient client = getQdrantClient();

        List<PointId> pointIds = new ArrayList<>();
        for (String uuid : uuids) {
            pointIds.add(id(UUID.fromString(uuid)));
        }

        ArrayList<String> payloads = new ArrayList<>();
        List<RetrievedPoint> points = client.retrieveAsync(COLLECTION_NAME, pointIds, null).get();
        for (RetrievedPoint point : points) {
            String payload = point.getPayloadMap().get("payload").getStringValue();
            payloads.add(payload);
        }

        return payloads;
    }

    public static void insertEmbedding(UUID uuid, List<Float> vectorData, String payload, String source)
            throws InterruptedException, ExecutionException {
        QdrantClient client = getQdrantClient();
        PointId pointId = id(uuid);
        PointStruct ps = PointStruct.newBuilder()
                .setId(pointId)
                .setVectors(vectors(vectorData))
                .putAllPayload(
                        Map.of(
                                "payload", value(payload), "source", value(source), "uuid", value(uuid.toString())))
                .build();

        UpdateResult updateResult = client.upsertAsync(COLLECTION_NAME, List.of(ps)).get();
        if (!updateResult.getStatus().equals(UpdateStatus.Completed)) {
            throw new RuntimeException(
                    "Failed to insert vector with ID: " + pointId + ", Status: " + updateResult.getStatus());
        }
    }
}
