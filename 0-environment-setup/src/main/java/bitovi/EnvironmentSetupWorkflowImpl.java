package bitovi;

import java.time.Duration;

import bitovi.activities.Zendesk;
import bitovi.activities.Bedrock;
import bitovi.activities.Postgres;
import bitovi.activities.S3;
import bitovi.activities.Qdrant;
import io.temporal.workflow.Workflow;
import io.temporal.activity.ActivityOptions;

public class EnvironmentSetupWorkflowImpl implements EnvironmentSetupWorkflow {
	private final ActivityOptions defaultActivityOptions = ActivityOptions
			.newBuilder()
			.setStartToCloseTimeout(Duration.ofSeconds(120))
			.build();

	private final Bedrock bedrockActivities = Workflow.newActivityStub(Bedrock.class, defaultActivityOptions);
	private final Postgres postgresActivities = Workflow.newActivityStub(Postgres.class, defaultActivityOptions);
	private final Qdrant qdrantActivites = Workflow.newActivityStub(Qdrant.class, defaultActivityOptions);
	private final S3 s3Activities = Workflow.newActivityStub(S3.class, defaultActivityOptions);
	private final Zendesk zendeskActivities = Workflow.newActivityStub(Zendesk.class, defaultActivityOptions);

	@Override
	public String execute() {
		bedrockActivities.checkBedrockConnection();
		postgresActivities.checkPostgresConnection();
		qdrantActivites.checkQdrantConnection();
		s3Activities.checkS3Connection();
		zendeskActivities.checkZendeskConnection();

		return "Success";
	}
}