package bitovi.common;

import bitovi.workflow.AgentToAgentWorkflow;
import bitovi.workflow.types.UsageMetadata;
import bitovi.workflow.types.WorkflowResult;
import io.temporal.client.WorkflowStub;

public class WorkflowHelper {

    public record FinalWorkflowResult(String finalAnswer, UsageMetadata usage) {
    }

    public static FinalWorkflowResult await(AgentToAgentWorkflow workflow) throws InterruptedException {
        // Because the Workflow is designed to run forever and wait for signals
        // we can poll to see if a final result has been produced.
        String finalAnswerReceived = null;
        while (finalAnswerReceived == null) {
            Thread.sleep(1000);
            finalAnswerReceived = workflow.getAnswer();
        }

        // Send exit signal
        workflow.requestExit();

        WorkflowResult result = WorkflowStub.fromTyped(workflow).getResult(WorkflowResult.class);
        return new FinalWorkflowResult(finalAnswerReceived, result.usage());
    }
}
