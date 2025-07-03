package bitovi.workflows.DocumentIngest.activities;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface DocumentIngestActivities {
    @ActivityMethod
    public void ingest(String documentPath);
}
