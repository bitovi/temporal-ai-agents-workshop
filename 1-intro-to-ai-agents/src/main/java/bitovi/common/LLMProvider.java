package bitovi.common;

import java.util.ArrayList;

public abstract class LLMProvider {
    public static abstract ArrayList<String> getModels() throws LLMProviderException;
}
