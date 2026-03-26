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
    private final QdrantGrpcClient grpcClient;

    private final String collectionName;

    @SuppressWarnings("null")
    public VectorDatabaseClient(String suffix) throws InterruptedException, ExecutionException {
        QdrantGrpcClient grpc = QdrantGrpcClient.newBuilder(QDRANT_HOST, QDRANT_PORT_GRPC, false)
                .build();
        QdrantClient temp = new QdrantClient(grpc);

        // Initialize the collection name based on the user ID
        this.collectionName = USER_ID + "_" + suffix;

        List<String> existing = temp.listCollectionsAsync().get();
        if (!existing.contains(collectionName)) {
            CollectionOperationResponse result = temp.createCollectionAsync(collectionName,
                    VectorParams.newBuilder()
                            .setDistance(Distance.Cosine)
                            .setSize(COLLECTION_VECTOR_SIZE)
                            .build())
                    .get();
            if (result.getResult()) {
                System.out.println("Collection '" + collectionName + "' created successfully.");
            } else {
                throw new RuntimeException("Failed to create collection '" + collectionName + "'.");
            }

            temp.createPayloadIndexAsync(
                    collectionName,
                    "created_at",
                    PayloadSchemaType.Datetime,
                    null,
                    true,
                    null,
                    null);

            temp.createPayloadIndexAsync(
                    collectionName,
                    "updated_at",
                    PayloadSchemaType.Datetime,
                    null,
                    true,
                    null,
                    null);
        }

        // Once we've gotten here, we have ensured that the collection exists
        this.client = temp;
        this.grpcClient = grpc;
    }

    public void close() {
        this.client.close();
        this.grpcClient.close();
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
}