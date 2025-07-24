package bitovi.common;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

public class AWS {
	private static Config config = new Config();

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
}