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

    private Logger logger = Workflow.getLogger("DocumentIngestImpl");

    @Override
    public void ingest(String documentsDirectory) {

        String[] files = activities.listTextFiles(documentsDirectory);

        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("Document list cannot be null or empty");
        }

        for (String path : files) {
            if (path == null || path.isEmpty()) {
                throw new IllegalArgumentException("Document path cannot be null or empty");
            }

            logger.info("Ingesting document from path: " + path);
            activities.ingest(documentsDirectory + "/" + path);
        }
    }
}
