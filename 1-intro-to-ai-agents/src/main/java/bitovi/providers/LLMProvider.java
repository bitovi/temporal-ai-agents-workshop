package bitovi.providers;

import java.util.ArrayList;
import java.util.List;

import bitovi.records.MessageRecord;

public interface LLMProvider {
    ArrayList<String> getModels() throws LLMProviderException;

    String completion(String prompt) throws LLMProviderException;

    List<List<Double>> embedding(List<String> text) throws LLMProviderException;

    MessageRecord chat(ArrayList<MessageRecord> prompt) throws LLMProviderException;
}
