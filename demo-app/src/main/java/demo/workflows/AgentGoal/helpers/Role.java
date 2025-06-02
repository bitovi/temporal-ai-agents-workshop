package demo.workflows.AgentGoal.helpers;

public enum Role {
    USER("user"),
    ASSISTANT("assistant");

    private final String role;

    Role(String role) {
        this.role = role;
    }

    public String getRole() {
        return role;
    }
}