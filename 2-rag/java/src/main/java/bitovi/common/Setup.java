package bitovi.common;

import java.io.File;

public class Setup {
    public static String[] uploadDocuments(String pathToDocuments) {
        // If the path ends with a slash, remove it
        if (pathToDocuments.endsWith(File.separator)) {
            pathToDocuments = pathToDocuments.substring(0, pathToDocuments.length() - 1);
        }

        // Get a list of all the .txt files in `pathToDocuments`
        File dir = new File(pathToDocuments);
        if (!dir.exists() || !dir.isDirectory()) {
            return new String[0];
        }

        String[] files = dir.list((d, name) -> name.endsWith(".txt"));
        // for each file, upload to S3 and return the S3 URL
        if (files == null || files.length == 0) {
            System.out.println("No .txt files found in the specified directory.");
            return new String[0];
        }

        String bucketName = new Config().getProperty("AWS_S3_BUCKET_NAME");

        // Create the s3 bucket if it doesn't exist
        AWS.createBucket(bucketName);

        String[] urls = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            String filePath = pathToDocuments + File.separator + files[i];
            String key = files[i] + "-" + System.currentTimeMillis(); // Use the file name as the key
            String s3Url = AWS.uploadFile(bucketName, key, filePath);
            urls[i] = s3Url;
        }

        return urls;
    }
}
