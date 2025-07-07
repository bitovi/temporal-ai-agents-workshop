package bitovi.workflows.DocumentIngest.activities;

import java.io.FileReader;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import bitovi.common.LLMProviderException;
import bitovi.common.database.QdrantWrapper;
import bitovi.providers.BaseModelProvider;
import bitovi.providers.OllamaProvider;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;

public class DocumentIngestActivitiesImpl implements DocumentIngestActivities {

    // Constructor to initialize the Qdrant collection
    public QdrantWrapper qdrant;
    public BaseModelProvider model;

    public DocumentIngestActivitiesImpl() throws InterruptedException, ExecutionException {
        this.qdrant = new QdrantWrapper();
        this.model = new OllamaProvider();
        qdrant.createCollection();
    }

    @Override
    public void ingest(String documentPath) {
        String content = readFile(documentPath);
        if (content.length() == 0) {
            System.out.println("Document " + documentPath + " is empty or could not be read");
            return;
        }

        // Split the document content into chunks
        DocumentByParagraphSplitter splitter = new DocumentByParagraphSplitter(500, 100);
        String[] chunks = splitter.split(content);

        for (String chunk : chunks) {
            if (chunk.length() > 0) {
                List<Float> embedding;
                try {
                    embedding = this.model.embedding(chunk);
                    UUID id = UUID.randomUUID(); // Generate a random UUID for the point ID
                    qdrant.insertEmbedding(id, embedding, chunk);
                } catch (LLMProviderException | InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private String readFile(String filePath) {
        StringBuilder content = new StringBuilder();
        try (FileReader reader = new FileReader(filePath)) {
            int c;
            while ((c = reader.read()) != -1) {
                content.append((char) c);
            }
        } catch (Exception e) {
            System.out.println("Error reading file: " + e.getMessage());
        }
        return content.toString();
    }
}
