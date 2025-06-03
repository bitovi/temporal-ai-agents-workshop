package demo.common;

public class LLMProviderException extends Exception {
    private String message;

    public LLMProviderException(String message) {
        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "LLMProviderError: " + message;
    }
}
