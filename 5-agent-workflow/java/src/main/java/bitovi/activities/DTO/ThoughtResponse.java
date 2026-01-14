package bitovi.activities.DTO;

public class ThoughtResponse {

    private String thought;
    private String answer;
    private Action action;

    public ThoughtResponse(String thought, String answer, Action action) {
        this.thought = thought;
        this.answer = answer;
        this.action = action;
    }

    public String getThought() {
        return thought;
    }

    public boolean isFinalAnswer() {
        return answer != null && !answer.isEmpty();
    }

    public String getAnswer() {
        return answer;
    }

}