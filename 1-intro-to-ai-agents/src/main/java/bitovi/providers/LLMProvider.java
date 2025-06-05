package bitovi.providers;

import java.util.ArrayList;
import java.util.List;

public interface LLMProvider {
    ArrayList<String> getModels() throws LLMProviderException;

    String completion(String prompt) throws LLMProviderException;

    List<List<Double>> embedding(List<String> text) throws LLMProviderException;

    LLMProviderChatMessage chat(ArrayList<LLMProviderChatMessage> prompt) throws LLMProviderException;
}
