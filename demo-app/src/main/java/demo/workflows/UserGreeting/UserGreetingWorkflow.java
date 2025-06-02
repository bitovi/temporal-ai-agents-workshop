package demo.workflows.UserGreeting;

import java.io.IOException;

import io.github.ollama4j.exceptions.OllamaBaseException;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface UserGreetingWorkflow {
    
    @WorkflowMethod
    String greetSomeone(String name) throws OllamaBaseException, IOException, InterruptedException;

}
