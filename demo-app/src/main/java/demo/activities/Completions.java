package demo.activities;

import java.io.IOException;

import io.github.ollama4j.exceptions.OllamaBaseException;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface Completions {

    @ActivityMethod
    public String generateGreeting(String name) throws OllamaBaseException, IOException, InterruptedException;
}