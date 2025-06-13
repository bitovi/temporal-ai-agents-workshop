package bitovi.providers;

import java.util.ArrayList;
import java.util.List;

import bitovi.DataTypes.MessageRecord;

public interface BaseModelProvider {
    ArrayList<String> getModels() throws LLMProviderException;

    List<List<Double>> embedding(List<String> text) throws LLMProviderException;

    MessageRecord chat(ArrayList<MessageRecord> prompt) throws LLMProviderException;
}
