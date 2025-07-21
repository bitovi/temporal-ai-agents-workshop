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

		String AWS_MODEL_ARN = config.getProperty("AWS_MODEL_ARN");
		String AWS_MODEL_ID = config.getProperty("AWS_MODEL_ID");
		String AWS_EMBEDDING_MODEL_ARN = config.getProperty("AWS_EMBEDDING_MODEL_ARN");
		String AWS_EMBEDDING_MODEL_ID = config.getProperty("AWS_EMBEDDING_MODEL_ID");

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

			boolean modelFound = models.stream()
					.anyMatch(model -> model.modelArn().equals(AWS_MODEL_ARN)
							&& model.modelId().equals(AWS_MODEL_ID)
							&& model.modelLifecycle().status().toString().equals("ACTIVE"));
			if (!modelFound) {
				throw ApplicationFailure.newNonRetryableFailure(
						"Model not found or not active: " + AWS_MODEL_ARN + " with ID: " + AWS_MODEL_ID,
						"BedrockError");
			}

			boolean embeddingModelFound = models.stream()
					.anyMatch(model -> model.modelArn().equals(AWS_EMBEDDING_MODEL_ARN)
							&& model.modelId().equals(AWS_EMBEDDING_MODEL_ID)
							&& model.modelLifecycle().status().toString().equals("ACTIVE"));
			if (!embeddingModelFound) {
				throw ApplicationFailure.newNonRetryableFailure(
						"Embedding model not found or not active: " + AWS_EMBEDDING_MODEL_ARN +
								" with ID: " + AWS_EMBEDDING_MODEL_ID,
						"BedrockError");
			}
		} catch (Exception e) {
			throw ApplicationFailure.newNonRetryableFailure(
					"Failed to connect to Bedrock: " + e.getMessage(),
					"BedrockError");
		}
	}
}