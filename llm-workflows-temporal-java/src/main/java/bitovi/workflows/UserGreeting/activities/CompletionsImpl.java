package bitovi.workflows.UserGreeting.activities;

import java.util.ArrayList;
import java.util.List;

import bitovi.common.Config;
import bitovi.common.LLMProviderException;
import bitovi.common.DataTypes.MessageRecord;
import bitovi.providers.OllamaProvider;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;

import io.temporal.activity.Activity;

public class CompletionsImpl implements Completions {

    String QDRANT_HOST = Config.getProperty("QDRANT_HOST");
    Integer QDRANT_PORT = Integer.parseInt(Config.getProperty("QDRANT_PORT", "6334"));
    QdrantClient qdrantClient = new QdrantClient(QdrantGrpcClient.newBuilder(QDRANT_HOST, QDRANT_PORT, false).build());

    @Override
    public String generateGreeting(String name) {
        try {
            OllamaProvider ollama = new OllamaProvider();
            ArrayList<MessageRecord> prompt = new ArrayList<>();
            prompt.add(new MessageRecord("user", "Name: " + name));

            String systemPrompt = "You are a basic greeting generator. You will be given a name and you should generate a simple friendly greeting for that name. You should respond with only the greeting, nothing else. Assume that the user is a human being and a friend of yours.";
            MessageRecord response = ollama.chat(prompt, systemPrompt);
            return response.content();
        } catch (LLMProviderException e) {
            throw Activity.wrap(e);
        }
    }

    @Override
    public List<List<Double>> generateEmbeddings(String text) {
        try {
            OllamaProvider ollama = new OllamaProvider();
            // Assuming the text is a single string, we can wrap it in a list
            return List.of(ollama.embedding(text)).stream()
                    .map(embedding -> embedding.stream().map(Double::valueOf).toList())
                    .toList();
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