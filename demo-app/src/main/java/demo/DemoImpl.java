package demo;

import java.io.IOException;
import java.time.Duration;

import demo.activities.CompletionsImpl;
import io.github.ollama4j.exceptions.OllamaBaseException;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;

public class DemoImpl implements Demo {
    ActivityOptions defaulActivityOptions = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(120))
            .build();

    private final CompletionsImpl activities = Workflow.newActivityStub(CompletionsImpl.class, defaulActivityOptions);

    @Override
    public String greetSomeone(String name) throws OllamaBaseException, IOException, InterruptedException {
        String aiUserGreeting = activities.generateGreeting(name);
        return aiUserGreeting;
    }
}
