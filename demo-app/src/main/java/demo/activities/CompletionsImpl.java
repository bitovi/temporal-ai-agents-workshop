package demo.activities;

import java.io.IOException;

import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.exceptions.OllamaBaseException;
import io.github.ollama4j.models.response.OllamaResult;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.temporal.activity.Activity;

public class CompletionsImpl implements Completions {

    QdrantClient client = new QdrantClient(QdrantGrpcClient.newBuilder("temporal-qdrant").build());

    @Override
    public String generateGreeting(String name) {
        StringBuilder builder = new StringBuilder();

        builder.append("Write a friendly greeting to a user named '")
                .append(name)
                .append("'");

        String host = "http://fractal.local.repkam09.com:11434/";

        OllamaAPI ollamaAPI = new OllamaAPI(host);

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
    public String generateEmbeddings(String text) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'generateEmbeddings'");
    }

    @Override
    public String generateCompletion(String prompt) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'generateCompletion'");
    }

    @Override
    public String[] searchEmbeddings(String query, Integer top) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'searchEmbeddings'");
    }
}