package bitovi.workflows.UserGreeting.activities;

import java.util.ArrayList;
import java.util.List;

import bitovi.common.LLMProviderException;
import bitovi.common.DataTypes.MessageRecord;
import bitovi.providers.OllamaProvider;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;

import io.temporal.activity.Activity;

public class CompletionsImpl implements Completions {

    QdrantClient qdrantClient = new QdrantClient(QdrantGrpcClient.newBuilder("temporal-qdrant", 6334, false).build());

    @Override
    public String generateGreeting(String name) {
        try {
            OllamaProvider ollama = new OllamaProvider();
            ArrayList<MessageRecord> prompt = new ArrayList<>();
            prompt.add(new MessageRecord("system",
                    "You are a basic greeting generator. You will be given a name and you should generate a simple friendly greeting for that name."));

            prompt.add(new MessageRecord("system",
                    "You should respond with only the greeting, nothing else. Assume that the user is a human being and a friend of yours."));

            prompt.add(new MessageRecord("user", "Name: " + name));

            MessageRecord response = ollama.chat(prompt);
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
            return ollama.embedding(List.of(text));
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