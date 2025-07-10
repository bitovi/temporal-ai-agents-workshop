package bitovi.activities;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import bitovi.common.AWS;
import bitovi.common.Config;
import bitovi.common.VectorDatabaseClient;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import io.temporal.failure.ApplicationFailure;

public class ActivitiesImpl implements Activities {
	@Override
	public void embed(String storageKey) throws ApplicationFailure {
		// Validate the key
		if (storageKey == null || storageKey.isEmpty()) {
			throw ApplicationFailure.newNonRetryableFailure("Key cannot be null or empty", "InvalidKey");
		}

		Config config = new Config();

		// Get the file from S3
		String outputPath = System.getProperty("java.io.tmpdir") + "/" + UUID.randomUUID().toString() + ".txt";
		String bucketName = config.getProperty("AWS_S3_BUCKET_NAME");

		Path path = AWS.downloadFile(bucketName, storageKey, outputPath);

		// Chunk and split the document into parts using DocumentByParagraphSplitter
		String content;
		try {
			content = Files.readString(path, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw ApplicationFailure.newNonRetryableFailure(
					"Failed to read file content: " + path.toAbsolutePath(), "FileReadError", e);
		}

		DocumentByParagraphSplitter splitter = new DocumentByParagraphSplitter(2500, 500);
		String[] chunks = splitter.split(content);

		for (String chunk : chunks) {
			if (chunk.length() > 0) {
				List<Float> embedding;
				try {
					embedding = AWS.calculateEmbedding(chunk);
				} catch (Exception e) {
					throw ApplicationFailure.newFailureWithCause(
							"Failed to get embedding for chunk", "EmbeddingError", e);
				}

				UUID id = UUID.randomUUID(); // Generate a random UUID for the point ID
				try {
					VectorDatabaseClient.insertEmbedding(id, embedding, chunk, storageKey);
				} catch (InterruptedException | ExecutionException e) {
					throw ApplicationFailure.newFailureWithCause(
							"Failed to insert embedding into vector database", "VectorDatabaseError", e);
				}
			}
		}

	}

	@Override
	public String search(String searchTerm) throws ApplicationFailure {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'search'");
	}
}
