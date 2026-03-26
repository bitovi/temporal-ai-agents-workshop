package bitovi.common.aws;

import bitovi.common.Config;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcorecontrol.BedrockAgentCoreControlClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

public class AWS {

    private static final Config config = new Config();

    public static AwsCredentialsProvider getAwsCredentialsProvider() {
        String AWS_ACCESS_KEY_ID = config.getProperty("AWS_ACCESS_KEY_ID");
        String AWS_SECRET_ACCESS_KEY = config.getProperty("AWS_SECRET_ACCESS_KEY");
        String AWS_SESSION_TOKEN = config.getProperty("AWS_SESSION_TOKEN");

        StaticCredentialsProvider credentialsProvider;

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
        return credentialsProvider;
    }

    public static Region getAwsRegion() {
        return Region.of(config.getProperty("AWS_REGION"));
    }

    public static BedrockRuntimeClient getBedrockRuntimeClient() {
        return BedrockRuntimeClient.builder()
                .credentialsProvider(AWS.getAwsCredentialsProvider())
                .region(AWS.getAwsRegion())
                .build();

    }

    public static BedrockAgentCoreClient getBedrockAgentCoreClient() {
        return BedrockAgentCoreClient.builder()
                .credentialsProvider(AWS.getAwsCredentialsProvider())
                .region(Region.of(config.getProperty("AWS_BEDROCK_AGENTCORE_MEMORY_REGION")))
                .build();
    }

    public static BedrockAgentCoreControlClient getBedrockAgentCoreControlClient() {
        return BedrockAgentCoreControlClient.builder()
                .credentialsProvider(AWS.getAwsCredentialsProvider())
                .region(Region.of(config.getProperty("AWS_BEDROCK_AGENTCORE_MEMORY_REGION")))
                .build();
    }
}
