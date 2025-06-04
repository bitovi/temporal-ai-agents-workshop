package bitovi.activities;

import java.util.List;

import bitovi.common.GenericLLMProvider;
import bitovi.common.LLMProviderException;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;

import io.temporal.activity.Activity;

public class CompletionsImpl implements Completions {

    QdrantClient qdrantClient = new QdrantClient(QdrantGrpcClient.newBuilder("temporal-qdrant", 6334, false).build());

    @Override
    public String generateGreeting(String name) {
        StringBuilder builder = new StringBuilder();
        builder.append("Write a friendly greeting to a user named '")
                .append(name)
                .append("'. You should respond with only the greeting, nothing else, assume that the user is a human being and a friend of yours.");

        try {
            return GenericLLMProvider.completion(builder.toString());
        } catch (LLMProviderException e) {
            throw Activity.wrap(e);
        }
    }

    @Override
    public List<List<Double>> generateEmbeddings(String text) {
        try {
            return GenericLLMProvider.generateEmbeddings(text);
        } catch (LLMProviderException e) {
            throw Activity.wrap(e);
        }
    }

    @Override
    public String generateCompletion(String prompt) {
        try {
            return GenericLLMProvider.completion(prompt);
        } catch (LLMProviderException e) {
            throw Activity.wrap(e);
        }
    }

    @Override
    public String[] searchEmbeddings(String query, Integer top) {
        createCollectionIfNotExists("embeddings");
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'searchEmbeddings'");
    }

    private void createCollectionIfNotExists(String collectionName) {
        try {
            // Check if collection exists by trying to get its info
            qdrantClient.getCollectionInfoAsync(collectionName).get();
        } catch (Exception e) {
            // Collection doesn't exist, create it
            try {
                qdrantClient.createCollectionAsync(collectionName,
                        VectorParams.newBuilder()
                                .setSize(1536)
                                .setDistance(Distance.Cosine)
                                .build())
                        .get();
            } catch (Exception createException) {
                throw new RuntimeException("Failed to create collection: " + collectionName, createException);
            }
        }
    }
}