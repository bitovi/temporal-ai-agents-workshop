package bitovi.activities;

import io.temporal.failure.ApplicationFailure;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;
import bitovi.common.Config;
import software.amazon.awssdk.regions.Region;

public class S3Impl implements S3 {

	@Override
	public void checkS3Connection() throws ApplicationFailure {
		Config config = new Config();

		String AWS_S3_ACCESS_KEY_ID = config.getProperty("AWS_ACCESS_KEY_ID");
		String AWS_S3_SECRET_ACCESS_KEY = config.getProperty("AWS_SECRET_ACCESS_KEY");
		String AWS_BUCKET_NAME = config.getProperty("AWS_S3_BUCKET_NAME");

		try {
			S3Client s3client = S3Client.builder()
					.credentialsProvider(
							StaticCredentialsProvider.create(
									AwsBasicCredentials.create(
											AWS_S3_ACCESS_KEY_ID,
											AWS_S3_SECRET_ACCESS_KEY)))
					.region(Region.of(config.getProperty("AWS_REGION")))
					.build();

			s3client.headBucket(b -> b.bucket(AWS_BUCKET_NAME));
		} catch (Exception e) {
			throw ApplicationFailure.newNonRetryableFailure(
					"Failed to connect to S3: " + e.getMessage(),
					"S3Error");
		} finally {

		}
	}
}