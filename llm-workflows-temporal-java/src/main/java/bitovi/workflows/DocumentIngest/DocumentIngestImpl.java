package bitovi.workflows.DocumentIngest;

import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;

import bitovi.common.Config;
import bitovi.common.database.QdrantWrapper;
import bitovi.providers.BaseModelProvider;
import bitovi.providers.OllamaProvider;
import bitovi.workflows.DocumentIngest.activities.DocumentIngestActivities;
import io.temporal.workflow.Workflow;

public class DocumentIngestImpl implements DocumentIngest {

    BaseModelProvider model;
    QdrantWrapper qdrant;

    public DocumentIngestImpl() throws InterruptedException, ExecutionException {
        this.qdrant = new QdrantWrapper();
        this.model = new OllamaProvider();
        qdrant.createCollection();

    }

    private final DocumentIngestActivities activities = Workflow.newActivityStub(DocumentIngestActivities.class,
            Config.getDefaultActivityOptions());

    private Logger logger = Workflow.getLogger("ChatWorkflowImpl");

    @Override
    public void ingest(String documentPath) {
        if (documentPath == null || documentPath.isEmpty()) {
            throw new IllegalArgumentException("Document path cannot be null or empty");
        }

        logger.info("Ingesting document from path: " + documentPath);
        activities.ingest(documentPath);
    }
}
