package bitovi.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.json.JSONObject;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

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

    public static Region getAwsRegion() {
        return Region.of(config.getProperty("AWS_REGION"));
    }

    public static S3Client getS3Client() {
        AwsCredentialsProvider credentialsProvider = AWS.getAwsCredentialsProvider();
        Region region = AWS.getAwsRegion();

        S3Client s3Client = S3Client.builder()
                .credentialsProvider(credentialsProvider)
                .region(region).build();

        return s3Client;
    }

    public static void createBucket(String bucketName) {
        // Create an S3Client
        S3Client s3Client = AWS.getS3Client();

        // Check if the bucket already exists
        if (s3Client.listBuckets().buckets().stream()
                .anyMatch(bucket -> bucket.name().equals(bucketName))) {
            System.out.println("Bucket already exists: " + bucketName);
            return;
        }

        // Create the bucket
        s3Client.createBucket(b -> b.bucket(bucketName));

        // Optionally, you can set the bucket policy or other configurations here
        System.out.println("Bucket created: " + bucketName);
    }

    public static GetObjectRequest createGetObjectRequest(String bucketName, String key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        return getObjectRequest;
    }

    public static PutObjectRequest createPutObjectRequest(String bucketName, String storageKey) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(storageKey)
                .build();

        return putObjectRequest;
    }

    public static String uploadFile(String bucketName, String storageKey, String inputFile) {
        // Create an S3Client
        S3Client s3Client = AWS.getS3Client();

        // Create a PutObjectRequest
        PutObjectRequest uploadFileRequest = AWS.createPutObjectRequest(bucketName, storageKey);

        // Upload the file
        s3Client.putObject(uploadFileRequest, RequestBody.fromFile(Paths.get(inputFile)));

        return storageKey;
    }

    public static Path downloadFile(String bucketName, String storageKey, String outputPath) {

        // Create an S3Client
        S3Client s3Client = AWS.getS3Client();

        // Create a GetObjectRequest
        GetObjectRequest getObjectRequest = AWS.createGetObjectRequest(bucketName, storageKey);

        // Download the file
        ResponseInputStream<GetObjectResponse> response = s3Client.getObject(getObjectRequest);

        Path p = Paths.get(outputPath);
        // Save the file to the specified output path

        try {
            Files.copy(response, p);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Close the response stream
        try {
            response.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return p;
    }

    public static BedrockRuntimeClient getBedrockRuntimeClient() {
        return BedrockRuntimeClient.builder()
                .credentialsProvider(AWS.getAwsCredentialsProvider())
                .region(AWS.getAwsRegion())
                .build();

    }

    public static List<Float> calculateEmbedding(String input) {
        String AWS_EMBEDDING_MODEL_ID = config.getProperty("AWS_EMBEDDING_MODEL_ID");

        JSONObject jsonBody = new JSONObject()
                .put("inputText", input);

        SdkBytes body = SdkBytes.fromUtf8String(jsonBody.toString());
        InvokeModelRequest request = InvokeModelRequest.builder()
                .modelId(AWS_EMBEDDING_MODEL_ID)
                .contentType("application/json")
                .accept("*/*")
                .body(body)
                .build();

        BedrockRuntimeClient bedrockRuntimeClient = AWS.getBedrockRuntimeClient();
        InvokeModelResponse response = bedrockRuntimeClient.invokeModel(request);

        JSONObject responseJson = new JSONObject(
                response.body().asString(StandardCharsets.UTF_8));

        List<Float> embedding = responseJson.getJSONArray("embedding").toList().stream()
                .map(obj -> ((Number) obj).floatValue())
                .toList();

        if (embedding.isEmpty()) {
            return List.of();
        }

        return embedding;
    }
}
