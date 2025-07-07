package bitovi.workflows.Chat.activities;

import java.util.ArrayList;
import java.util.List;

import bitovi.common.DataTypes;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface ChatActivities {
    @ActivityMethod
    public String chat(ArrayList<DataTypes.MessageRecord> history, String[] uuids);

    @ActivityMethod
    public String[] search(List<Float> search);

    @ActivityMethod
    public List<Float> embedding(String text);
}