package bitovi.workflows.Chat;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface ChatWorkflow {

    @WorkflowMethod
    void run();

    @SignalMethod
    void prompt(String prompt);

    @QueryMethod
    String getLastResponse();
}
