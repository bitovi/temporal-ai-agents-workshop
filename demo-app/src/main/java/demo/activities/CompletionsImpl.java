package demo.activities;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.exceptions.OllamaBaseException;
import io.github.ollama4j.models.embeddings.OllamaEmbedResponseModel;
import io.github.ollama4j.models.response.OllamaResult;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;

import io.temporal.activity.Activity;

public class CompletionsImpl implements Completions {

    QdrantClient qdrantClient = new QdrantClient(QdrantGrpcClient.newBuilder("temporal-qdrant", 6334, false).build());

    @Override
    public String generateGreeting(String name) {
        OllamaAPI ollamaAPI = getOllamaAPI();

        StringBuilder builder = new StringBuilder();
        builder.append("Write a friendly greeting to a user named '")
                .append(name)
                .append("'. You should respond with only the greeting, nothing else, assume that the user is a human being and a friend of yours.");

        OllamaResult result;
        try {
            // Generate a greeting using the Ollama API
            result = ollamaAPI.generate("gemma3:4b", builder.toString(), null);
        } catch (OllamaBaseException | IOException | InterruptedException e) {
            throw Activity.wrap(e);
        }

        String greeting = result.getResponse();
        return greeting;
    }

    @Override
    public List<List<Double>> generateEmbeddings(String text) {
        createCollectionIfNotExists("embeddings");

        OllamaAPI ollamaAPI = getOllamaAPI();

        OllamaEmbedResponseModel result;
        try {
            // Generate a greeting using the Ollama API
            result = ollamaAPI.embed("gemma3:4b", Arrays.asList(text));
        } catch (OllamaBaseException | IOException | InterruptedException e) {
            throw Activity.wrap(e);
        }

        List<List<Double>> embeddings = result.getEmbeddings();
        return embeddings;
    }

    @Override
    public String generateCompletion(String prompt) {
        OllamaAPI ollamaAPI = getOllamaAPI();

        OllamaResult result;
        try {
            // Generate a greeting using the Ollama API
            result = ollamaAPI.generate("gemma3:4b", prompt, null);
        } catch (OllamaBaseException | IOException | InterruptedException e) {
            throw Activity.wrap(e);
        }

        String response = result.getResponse();
        return response;
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

    private OllamaAPI getOllamaAPI() {
        String host = "http://fractal.local.repkam09.com:11434/";
        return new OllamaAPI(host);
    }
}