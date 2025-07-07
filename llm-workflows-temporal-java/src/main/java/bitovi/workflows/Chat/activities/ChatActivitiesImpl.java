package bitovi.workflows.Chat.activities;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import bitovi.common.LLMProviderException;
import bitovi.common.database.QdrantWrapper;
import bitovi.common.DataTypes.MessageRecord;
import bitovi.providers.BaseModelProvider;
import bitovi.providers.OllamaProvider;
import io.temporal.activity.Activity;

public class ChatActivitiesImpl implements ChatActivities {

    // Constructor to initialize the Qdrant collection
    public QdrantWrapper qdrant;
    public BaseModelProvider model;

    public ChatActivitiesImpl() throws InterruptedException, ExecutionException {
        this.qdrant = new QdrantWrapper();
        this.model = new OllamaProvider();
        qdrant.createCollection();
    }

    @Override
    public String chat(ArrayList<MessageRecord> history, String[] uuids) {
        // Add the system prompt to the beginning of the chat history
        if (history == null || history.isEmpty()) {
            throw Activity.wrap(new IllegalArgumentException("Chat history cannot be null or empty"));
        }

        // Fetch context from Qdrant using the provided UUIDs
        StringBuilder context = new StringBuilder();
        if (uuids != null && uuids.length > 0) {
            for (String uuid : uuids) {
                try {
                    String payload = this.qdrant.getPayloadById(uuid);
                    if (payload != null) {
                        context.append(payload).append(" ");
                    }
                } catch (InterruptedException | ExecutionException e) {
                    throw Activity.wrap(e);
                }
            }
        }

        MessageRecord result;
        try {
            // Convert MessageRecord to MessageRecord
            ArrayList<MessageRecord> chatHistory = new ArrayList<MessageRecord>();
            for (MessageRecord record : history) {
                MessageRecord message = new MessageRecord(record.role() != null ? record.role() : "user",
                        record.content());
                chatHistory.add(message);
            }

            StringBuilder systemBuilder = new StringBuilder();
            systemBuilder.append("You are a Software Engineer working at Bitovi.");
            systemBuilder.append("Your name is Mark Repka.");
            systemBuilder.append("You have worked at Bitovi for 4.5 years on the Systems Engineering team.");
            systemBuilder.append("You specialize in AI, Machine Learning, and Temporal.io Workflows.");
            systemBuilder.append("You are helping a user with their questions about AI Agents and Temporal Workflows.");
            if (context != null && !context.isEmpty()) {
                systemBuilder.append("Here is some additional context fetched from a knowledgebase: ").append(context)
                        .append(" ");
                System.out.println("Context found in knowledgebase: " + context);
            } else {
                systemBuilder.append(
                        "No additional context was found in the knowledgebase. Just answer the user's question to the best of your ability. ");
            }
            systemBuilder.append("Here is the conversation you are having with your coworker:");

            result = this.model.chat(chatHistory, systemBuilder.toString());
        } catch (LLMProviderException e) {
            throw Activity.wrap(e);
        }
        return result.content();
    }

    @Override
    public String[] search(List<Float> search) {
        if (this.qdrant == null) {
            throw Activity.wrap(new IllegalStateException("QdrantWrapper is not initialized"));
        }

        try {
            return this.qdrant.search(search);
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw Activity.wrap(e);

        }
    }

    @Override
    public List<Float> embedding(String text) {
        if (this.qdrant == null) {
            throw Activity.wrap(new IllegalStateException("QdrantWrapper is not initialized"));
        }

        try {
            return this.model.embedding(text);
        } catch (LLMProviderException e) {
            e.printStackTrace();
            throw Activity.wrap(e);
        }
    }
}
