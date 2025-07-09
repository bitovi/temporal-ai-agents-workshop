package bitovi.activities;

import java.util.List;

import bitovi.common.Config;

import io.temporal.failure.ApplicationFailure;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.bedrock.BedrockClient;
import software.amazon.awssdk.services.bedrock.model.FoundationModelSummary;
import software.amazon.awssdk.services.bedrock.model.ListFoundationModelsResponse;
import software.amazon.awssdk.regions.Region;

public class BedrockImpl implements Bedrock {
	@Override
	public void checkBedrockConnection() throws ApplicationFailure {
		Config config = new Config();

		String AWS_ACCESS_KEY_ID = config.getProperty("AWS_ACCESS_KEY_ID");
		String AWS_SECRET_ACCESS_KEY = config.getProperty("AWS_SECRET_ACCESS_KEY");
		String AWS_SESSION_TOKEN = config.getProperty("AWS_SESSION_TOKEN");
		String AWS_REGION = config.getProperty("AWS_REGION");

		try {
			BedrockClient bedrockClient = BedrockClient.builder()
					.credentialsProvider(
							StaticCredentialsProvider.create(
									AwsSessionCredentials.create(
											AWS_ACCESS_KEY_ID,
											AWS_SECRET_ACCESS_KEY,
											AWS_SESSION_TOKEN)))
					.region(Region.of(AWS_REGION))
					.build();

			ListFoundationModelsResponse response = bedrockClient.listFoundationModels(r -> {
			});
			List<FoundationModelSummary> models = response.modelSummaries();

			if (models.isEmpty()) {
				throw ApplicationFailure.newNonRetryableFailure(
						"No foundation models found. Please check your AWS credentials and region.",
						"BedrockError");
			}
		} catch (Exception e) {
			throw ApplicationFailure.newNonRetryableFailure(
					"Failed to connect to Bedrock: " + e.getMessage(),
					"BedrockError");
		}
	}
}