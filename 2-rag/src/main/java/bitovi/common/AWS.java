package bitovi.common;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.json.JSONObject;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest;
import software.amazon.awssdk.transfer.s3.model.FileDownload;
import software.amazon.awssdk.transfer.s3.model.FileUpload;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;

public class AWS {
    private static Config config = new Config();

    public static AwsCredentialsProvider getAwsCredentialsProvider() {
        String AWS_ACCESS_KEY_ID = config.getProperty("AWS_ACCESS_KEY_ID");
        String AWS_SECRET_ACCESS_KEY = config.getProperty("AWS_SECRET_ACCESS_KEY");
        String AWS_SESSION_TOKEN = config.getProperty("AWS_SESSION_TOKEN");

        return StaticCredentialsProvider.create(
                AwsSessionCredentials.create(
                        AWS_ACCESS_KEY_ID,
                        AWS_SECRET_ACCESS_KEY,
                        AWS_SESSION_TOKEN));
    }

    public static AwsCredentialsProvider getAwsLocalstackCredentialsProvider() {
        String AWS_S3_ACCESS_KEY_ID = config.getProperty("AWS_S3_ACCESS_KEY_ID");
        String AWS_S3_SECRET_ACCESS_KEY = config.getProperty("AWS_S3_SECRET_ACCESS_KEY");

        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                        AWS_S3_ACCESS_KEY_ID,
                        AWS_S3_SECRET_ACCESS_KEY));
    }

    public static Region getAwsRegion() {
        return Region.of(config.getProperty("AWS_REGION"));
    }

    public static S3AsyncClient getAsyncClient() {
        AwsCredentialsProvider credentialsProvider = AWS.getAwsLocalstackCredentialsProvider();
        Region region = AWS.getAwsRegion();

        String endpointOverride = config.getProperty("AWS_S3_ENDPOINT_URL");

        S3AsyncClientBuilder s3AsyncClient = S3AsyncClient.builder()
                .credentialsProvider(credentialsProvider)
                .endpointOverride(URI.create(endpointOverride))
                .region(region);

        return s3AsyncClient.build();
    }

    public static S3TransferManager getTransferManager() {
        S3AsyncClient s3AsyncClient = AWS.getAsyncClient();

        S3TransferManager transferManager = S3TransferManager.builder()
                .s3Client(s3AsyncClient)
                .build();

        return transferManager;
    }

    public static DownloadFileRequest createDownloadFileRequest(String bucketName, String key, String outputFile) {
        DownloadFileRequest downloadFileRequest = DownloadFileRequest.builder()
                .getObjectRequest(b -> b.bucket(bucketName).key(key))
                .destination(Paths.get(outputFile))
                .build();

        return downloadFileRequest;
    }

    public static UploadFileRequest createUploadFileRequest(String bucketName, String storageKey, String inputFile) {
        UploadFileRequest uploadFileRequest = UploadFileRequest.builder()
                .putObjectRequest(b -> b.bucket(bucketName).key(storageKey))
                .source(Paths.get(inputFile))
                .build();

        return uploadFileRequest;
    }

    public static String uploadFile(String bucketName, String storageKey, String inputFile) {
        // Create an S3TransferManager
        S3TransferManager transferManager = AWS.getTransferManager();

        // Create an UploadFileRequest
        UploadFileRequest uploadFileRequest = AWS.createUploadFileRequest(bucketName, storageKey, inputFile);

        // Upload the file
        FileUpload fileUpload = transferManager.uploadFile(uploadFileRequest);
        fileUpload.completionFuture().join();

        transferManager.close();
        return storageKey;
    }

    public static Path downloadFile(String bucketName, String storageKey, String outputPath) {

        // Create an S3TransferManager
        S3TransferManager transferManager = AWS.getTransferManager();

        // Create a DownloadFileRequest
        DownloadFileRequest downloadFileRequest = AWS.createDownloadFileRequest(bucketName, storageKey, outputPath);

        // Download the file
        FileDownload downloadFile = transferManager.downloadFile(downloadFileRequest);
        downloadFile.completionFuture().join();

        transferManager.close();
        return new File(outputPath).toPath();
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
