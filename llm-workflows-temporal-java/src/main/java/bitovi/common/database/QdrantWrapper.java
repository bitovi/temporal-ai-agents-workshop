package bitovi.common.database;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import bitovi.common.Config;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.CollectionOperationResponse;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.PointId;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.SearchPoints;
import io.qdrant.client.grpc.Points.UpdateResult;
import io.qdrant.client.grpc.Points.UpdateStatus;

import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;

public class QdrantWrapper {

    private QdrantClient client;
    private static final String COLLECTION_NAME = "bitovi";

    /*
     * Amazon Titan Embeddings G1 - Text Floating-point 1536
     * Amazon Titan Text Embeddings V2 Floating-point, binary 256, 512, 1024
     * Cohere Embed (English) Floating-point, binary 1024
     * Cohere Embed (Multilingual) Floating-point, binary 1024
     */
    private static final Integer VECTOR_SIZE = 1024;

    public QdrantWrapper() throws InterruptedException, ExecutionException {
        String host = Config.getProperty("QDRANT_HOST", "localhost");
        Integer port = Integer.parseInt(Config.getProperty("QDRANT_PORT", "6334"));
        String apiKey = Config.getProperty("QDRANT_SERVICE_API_KEY");

        QdrantGrpcClient grpc = QdrantGrpcClient.newBuilder(host, port, false).withApiKey(apiKey).build();
        this.client = new QdrantClient(grpc);

        listCollections();
    }

    public List<String> listCollections() {
        try {
            List<String> list = this.client.listCollectionsAsync().get();
            System.out.println("Found collections: " + list);
            return list;
        } catch (Exception e) {
            System.err.println("Error listing collections: " + e.getMessage());
            return List.of();
        }
    }

    public void createCollection() throws InterruptedException, ExecutionException {
        // Check if the collection already exists
        List<String> existing = this.listCollections();
        if (existing.contains(COLLECTION_NAME)) {
            System.out.println("Collection '" + COLLECTION_NAME + "' already exists.");
            return;
        }

        CollectionOperationResponse result = this.client.createCollectionAsync(COLLECTION_NAME,
                VectorParams.newBuilder()
                        .setDistance(Distance.Cosine)
                        .setSize(VECTOR_SIZE)
                        .build())
                .get();
        if (result.getResult()) {
            System.out.println("Collection '" + COLLECTION_NAME + "' created successfully.");
        } else {
            System.err.println("Failed to create collection '" + COLLECTION_NAME + "'.");
        }
    }

    public Points.ScoredPoint search(List<Float> queryVector) throws InterruptedException, ExecutionException {
        List<Points.ScoredPoint> points = this.client
                .searchAsync(
                        SearchPoints.newBuilder()
                                .setCollectionName(COLLECTION_NAME)
                                .addAllVector(queryVector)
                                .setLimit(1)
                                .build())
                .get();
        return points.isEmpty() ? null : points.get(0);
    }

    /**
     * Inserts an embedding into the Qdrant collection.
     * 
     * @param collectionName
     * @param uuid
     * @param vectorId
     * @param vectorData     This should be a list of Float with length VECTOR_SIZE.
     * @param payload
     * @throws InterruptedException
     * @throws ExecutionException
     */
    public void insertEmbedding(UUID uuid, List<Float> vectorData, String payload)
            throws InterruptedException, ExecutionException {
        PointId pointId = id(uuid);
        PointStruct ps = PointStruct.newBuilder()
                .setId(pointId)
                .setVectors(vectors(vectorData))
                .putAllPayload(
                        Map.of(
                                "payload", value(payload)))
                .build();

        UpdateResult updateResult = client.upsertAsync(COLLECTION_NAME, List.of(ps)).get();
        if (updateResult.getStatus().equals(UpdateStatus.Completed)) {
            System.out.println("Successfully inserted vector with ID: " + pointId);
        } else {
            System.err
                    .println("Failed to insert vector with ID: " + pointId + ", Status: " + updateResult.getStatus());
        }
    }
}
