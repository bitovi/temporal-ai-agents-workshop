package bitovi.providers;

public class LLMProviderChatMessage {
    private String role;
    private String content;

    public LLMProviderChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    @Override
    public String toString() {
        return role + ": " + content;
    }
}
