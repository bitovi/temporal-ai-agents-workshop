package bitovi.activities;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import bitovi.common.AWS;
import bitovi.common.Config;
import bitovi.common.VectorDatabaseClient;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentByCharacterSplitter;
import dev.langchain4j.data.segment.TextSegment;
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
		Document doc;
		try {
			InputStream input = Files.newInputStream(path);
			doc = new TextDocumentParser().parse(input);
		} catch (IOException e) {
			throw ApplicationFailure.newNonRetryableFailure(
					"Failed to read file content: " + path.toAbsolutePath(), "FileReadError", e);
		}

		DocumentSplitter splitter = new DocumentByCharacterSplitter(1024, 250);
		List<TextSegment> chunks = splitter.split(doc);

		for (TextSegment chunk : chunks) {
			String text = chunk.text();
			if (text != null && !text.isEmpty()) {
				System.out.println("Chunk: " + text);
				List<Float> embedding;
				try {
					embedding = AWS.calculateEmbedding(text);
				} catch (Exception e) {
					throw ApplicationFailure.newFailureWithCause(
							"Failed to get embedding for chunk", "EmbeddingError", e);
				}

				UUID id = UUID.randomUUID(); // Generate a random UUID for the point ID
				try {
					VectorDatabaseClient.insertEmbedding(id, embedding, text, storageKey);
				} catch (InterruptedException | ExecutionException e) {
					throw ApplicationFailure.newFailureWithCause(
							"Failed to insert embedding into vector database", "VectorDatabaseError", e);
				}
			}
		}

	}

	@Override
	public ArrayList<String> search(String searchTerm) throws ApplicationFailure {
		List<Float> embedding;
		try {
			embedding = AWS.calculateEmbedding(searchTerm);
		} catch (Exception e) {
			throw ApplicationFailure.newFailureWithCause(
					"Failed to get embedding for chunk", "EmbeddingError", e);
		}

		try {
			String[] uuids = VectorDatabaseClient.searchVectorDatabase(embedding, 5);
			if (uuids.length == 0) {
				return null;
			}

			ArrayList<String> results = VectorDatabaseClient.getPayloadsByIds(uuids);
			if (results.isEmpty()) {
				return null;
			}

			return results;

		} catch (InterruptedException | ExecutionException e) {
			throw ApplicationFailure.newFailureWithCause(
					"Failed to insert embedding into vector database", "VectorDatabaseError", e);
		}
	}

}
