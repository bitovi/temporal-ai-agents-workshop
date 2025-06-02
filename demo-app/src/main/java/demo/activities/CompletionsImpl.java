package demo.activities;

import java.io.IOException;

import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.exceptions.OllamaBaseException;
import io.github.ollama4j.models.response.OllamaResult;

public class CompletionsImpl implements Completions {

    @Override
    public String generateGreeting(String name) throws OllamaBaseException, IOException, InterruptedException {
        StringBuilder builder = new StringBuilder();

        builder.append("Write a friendly greeting to a user named '")
                .append(name)
                .append("'");

        String host = "http://localhost:11434/";

        OllamaAPI ollamaAPI = new OllamaAPI(host);

        OllamaResult result = ollamaAPI.generate("gemma3:4b", builder.toString(), null);

        String greeting = result.getResponse();

        return greeting;
    }
}