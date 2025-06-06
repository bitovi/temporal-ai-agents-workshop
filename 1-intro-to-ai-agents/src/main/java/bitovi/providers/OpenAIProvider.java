package bitovi.providers;

import java.util.ArrayList;
import java.util.List;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;

import bitovi.Config;

public class OpenAIProvider implements LLMProvider {

    private static String OPENAI_MODEL_ID;
    private static String OPENAI_BASE_URL;

    private OpenAIClient openAIClient;

    public OpenAIProvider() {
        this.OPENAI_MODEL_ID = Config.getProperty("OPENAI_MODEL_ID");
        this.OPENAI_BASE_URL = Config.getProperty("OPENAI_BASE_URL");

        this.openAIClient = OpenAIOkHttpClient.builder()
                .apiKey(Config.getProperty("OPENAI_API_KEY"))
                .baseUrl(OPENAI_BASE_URL)
                .build();
    }

    @Override
    public ArrayList<String> getModels() throws LLMProviderException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getModels'");
    }

    @Override
    public String completion(String prompt) throws LLMProviderException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'completion'");
    }

    @Override
    public List<List<Double>> embedding(List<String> text) throws LLMProviderException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'embedding'");
    }

    @Override
    public LLMProviderChatMessage chat(ArrayList<LLMProviderChatMessage> prompt) throws LLMProviderException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'chat'");
    }

}
