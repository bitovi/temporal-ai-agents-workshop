package bitovi.workflows.DocumentIngest;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface DocumentIngest {

    @WorkflowMethod
    void ingest(String documentPath);
}
