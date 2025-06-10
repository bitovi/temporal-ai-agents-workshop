package bitovi.activities;

import java.util.ArrayList;

import bitovi.records.MessageRecord;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface ChatActivities {
    @ActivityMethod
    public String chat(ArrayList<MessageRecord> history);
}