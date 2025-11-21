package bitovi.activities;

import bitovi.common.Config;


import io.temporal.failure.ApplicationFailure;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.ListMemoryRecordsRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.ListMemoryRecordsResponse;


public class BedrockImpl implements Bedrock {
	@Override
	public String checkBedrockMemoryConnection() throws ApplicationFailure {
		Config config = new Config();

		String AWS_ACCESS_KEY_ID = config.getProperty("AWS_ACCESS_KEY_ID");
		String AWS_SECRET_ACCESS_KEY = config.getProperty("AWS_SECRET_ACCESS_KEY");
		String AWS_SESSION_TOKEN = config.getProperty("AWS_SESSION_TOKEN");
		String AWS_REGION = config.getProperty("AWS_REGION");

		StaticCredentialsProvider credentialsProvider;

		try {
			if (AWS_SESSION_TOKEN == null || AWS_SESSION_TOKEN.isEmpty()) {
				credentialsProvider = StaticCredentialsProvider.create(
						AwsBasicCredentials.create(
								AWS_ACCESS_KEY_ID,
								AWS_SECRET_ACCESS_KEY));
			} else {
				credentialsProvider = StaticCredentialsProvider.create(
						AwsSessionCredentials.create(
								AWS_ACCESS_KEY_ID,
								AWS_SECRET_ACCESS_KEY,
								AWS_SESSION_TOKEN));
			}
		} catch (Exception e) {
			throw ApplicationFailure.newNonRetryableFailure(
					"Failed to create AWS credentials: " + e.getMessage(),
					"AWSCredentialsError");
		}

		BedrockAgentCoreClient bedrockAgentCoreClient;

		try {
			bedrockAgentCoreClient = BedrockAgentCoreClient.builder()
					.credentialsProvider(credentialsProvider)
					.region(Region.of(AWS_REGION))
					.build();

		} catch (Exception e) {
			throw ApplicationFailure.newNonRetryableFailure(
					"Failed to create Bedrock client: " + e.getMessage(),
					"BedrockClientError");
		}

		ListMemoryRecordsResponse results;
		try {
			ListMemoryRecordsRequest listRequest = ListMemoryRecordsRequest.builder()
					.memoryId(config.getProperty("AWS_BEDROCK_MEMORY_ID"))
					.build();

			results = bedrockAgentCoreClient.listMemoryRecords(listRequest);
		} catch (Exception e) {
			throw ApplicationFailure.newNonRetryableFailure(
					"Failed to list memory records with Bedrock AgentCore: " + e.getMessage(),
					"BedrockError");
		}

		// Process the results as needed
		System.out.println("Number of memory records retrieved: " + results.memoryRecordSummaries().size());

		return "Bedrock AgentCore connection successful.";
	}
}