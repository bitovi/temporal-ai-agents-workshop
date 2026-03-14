package bitovi;

import java.time.Duration;

import org.slf4j.Logger;

import bitovi.activities.AgentChatServer;
import bitovi.activities.Bedrock;
import bitovi.activities.MockMcpServer;
import bitovi.activities.Postgres;
import bitovi.activities.Qdrant;
import bitovi.activities.S3;
import bitovi.activities.SupportAgentServer;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

public class EnvironmentSetupWorkflowImpl implements EnvironmentSetupWorkflow {
	private final ActivityOptions defaultActivityOptions = ActivityOptions
			.newBuilder()
			.setStartToCloseTimeout(Duration.ofSeconds(120))
			.setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
			.setScheduleToStartTimeout(Duration.ofSeconds(60))
			.build();

	private static final Logger logger = Workflow.getLogger(EnvironmentSetupWorkflowImpl.class);

	private final Bedrock bedrockActivities = Workflow.newActivityStub(Bedrock.class, defaultActivityOptions);
	private final Postgres postgresActivities = Workflow.newActivityStub(Postgres.class, defaultActivityOptions);
	private final Qdrant qdrantActivites = Workflow.newActivityStub(Qdrant.class, defaultActivityOptions);
	private final S3 s3Activities = Workflow.newActivityStub(S3.class, defaultActivityOptions);
	private final MockMcpServer mockMcpServerActivities = Workflow.newActivityStub(MockMcpServer.class,
			defaultActivityOptions);
	private final AgentChatServer agentChatServerActivities = Workflow.newActivityStub(AgentChatServer.class,
			defaultActivityOptions);
	private final SupportAgentServer supportAgentServerActivities = Workflow.newActivityStub(SupportAgentServer.class,
			defaultActivityOptions);

	@Override
	public String execute() {

		logger.info("Starting Environment Setup Workflow...");
		bedrockActivities.checkBedrockConnection();

		logger.info("checkPostgresConnection");
		postgresActivities.checkPostgresConnection();

		logger.info("checkQdrantConnection");
		qdrantActivites.checkQdrantConnection();

		logger.info("checkS3Connection");
		s3Activities.checkS3Connection();

		logger.info("checkMockMcpServerConnection");
		mockMcpServerActivities.checkMockMcpServerConnection();

		logger.info("checkAgentChatServerConnection");
		agentChatServerActivities.checkAgentChatServerConnection();

		logger.info("checkSupportAgentServerConnection");
		supportAgentServerActivities.checkSupportAgentServerConnection();

		return "Success";
	}
}