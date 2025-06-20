package bitovi.providers;

import java.util.ArrayList;
import java.util.List;

import bitovi.common.LLMProviderException;
import bitovi.common.DataTypes.MessageRecord;

public interface BaseModelProvider {
    ArrayList<String> getModels() throws LLMProviderException;

    List<Float> embedding(String text) throws LLMProviderException;

    MessageRecord chat(ArrayList<MessageRecord> prompt, String systemPrompt) throws LLMProviderException;
}
