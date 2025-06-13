package bitovi.workflows.Chat.activities;

import java.util.ArrayList;

import bitovi.common.DataTypes;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface ChatActivities {
    @ActivityMethod
    public String chat(ArrayList<DataTypes.MessageRecord> history);
}