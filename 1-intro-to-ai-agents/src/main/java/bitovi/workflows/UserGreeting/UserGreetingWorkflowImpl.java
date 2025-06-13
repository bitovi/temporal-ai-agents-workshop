package bitovi.workflows.UserGreeting;

import java.io.IOException;

import bitovi.Config;
import bitovi.workflows.UserGreeting.activities.Completions;
import io.github.ollama4j.exceptions.OllamaBaseException;
import io.temporal.workflow.Workflow;

public class UserGreetingWorkflowImpl implements UserGreetingWorkflow {
    // This is the activity stub of the INTERFACE, not the implementation.
    private final Completions activities = Workflow.newActivityStub(Completions.class,
            Config.getDefaultActivityOptions());

    @Override
    public String greetSomeone(String name) throws OllamaBaseException, IOException, InterruptedException {
        String aiUserGreeting = activities.generateGreeting(name);
        return aiUserGreeting;
    }
}
