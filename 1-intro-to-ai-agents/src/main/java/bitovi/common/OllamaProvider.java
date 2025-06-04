package bitovi.common;

import java.util.ArrayList;
import java.util.List;

import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.models.response.Model;

public class OllamaProvider {
    private static OllamaAPI getOllamaInstance() {
        OllamaAPI ollama = new OllamaAPI("http://fractal.local.repkam09.com:11434/");
        ollama.setVerbose(true);
        ollama.setRequestTimeoutSeconds(120);
        return ollama;
    }

    public static ArrayList<String> getModels() throws LLMProviderException {
        var ollama = getOllamaInstance();
        ArrayList<String> modelNames = new ArrayList<>();

        try {
            List<Model> models = ollama.listModels();

            for (var model : models) {
                modelNames.add(model.getName());
            }
        } catch (Exception e) {
            throw new LLMProviderException("Error fetching models: " + e.getMessage());

            return modelNames;
        }
    }

}
