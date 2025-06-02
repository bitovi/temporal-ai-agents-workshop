package demo.activities;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface Completions {

    @ActivityMethod
    public String generateGreeting(String name);

    @ActivityMethod
    public String generateEmbeddings(String text);

    @ActivityMethod
    public String generateCompletion(String prompt);

    @ActivityMethod
    public String[] searchEmbeddings(String query, Integer top);
}