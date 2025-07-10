package bitovi.activities;

import io.temporal.failure.ApplicationFailure;
import bitovi.common.Config;

public class S3Impl implements S3 {

	@Override
	public void checkS3Connection() throws ApplicationFailure {
		Config config = new Config();
		String AWS_S3_ACCESS_KEY_ID = config.getProperty("AWS_S3_ACCESS_KEY_ID");
		String AWS_S3_SECRET_ACCESS_KEY = config.getProperty("AWS_S3_SECRET_ACCESS_KEY");
		String AWS_S3_BUCKET_NAME = config.getProperty("AWS_S3_BUCKET_NAME");

	}
}