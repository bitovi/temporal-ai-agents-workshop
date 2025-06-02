package demo;

import java.io.IOException;
import java.time.Duration;

import demo.activities.Completions;
import io.github.ollama4j.exceptions.OllamaBaseException;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;

public class DemoImpl implements Demo {
    ActivityOptions defaulActivityOptions = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(120))
            .build();

    // This is the activity stub of the INTERFACE, not the implementation.
    private final Completions activities = Workflow.newActivityStub(Completions.class, defaulActivityOptions);

    @Override
    public String greetSomeone(String name) throws OllamaBaseException, IOException, InterruptedException {
        String aiUserGreeting = activities.generateGreeting(name);
        return aiUserGreeting;
    }
}
