package bitovi.demos;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import bitovi.common.LLMProviderException;
import bitovi.common.database.QdrantWrapper;
import bitovi.providers.BedrockProvider;
import io.qdrant.client.grpc.Points.ScoredPoint;

public class BitoviQdrantReadWrite {
    public static void main(String[] args) throws InterruptedException, ExecutionException, LLMProviderException {

        QdrantWrapper qdrant = new QdrantWrapper();
        qdrant.createCollection();

        BedrockProvider bedrock = new BedrockProvider();

        List<String> text = List.of(
                "Hello world! This is a test.",
                "This is another test sentence.",
                "Qdrant is a vector database for AI applications.");

        for (String str : text) {
            List<Float> embedding = bedrock.embedding(str);
            UUID id = UUID.randomUUID(); // Generate a random UUID for the point ID
            qdrant.insertEmbedding(id, embedding, str);
        }

        String searchQuery = "Qdrant is";
        List<Float> queryEmbedding = bedrock.embedding(searchQuery);

        ScoredPoint results = qdrant.search(queryEmbedding);
        System.out.println("Search results for query '" + searchQuery + "': " + results.toString());
    }
}
