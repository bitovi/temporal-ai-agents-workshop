package bitovi.workflows.Chat.activities;

import java.util.ArrayList;
import java.util.List;

import bitovi.common.DataTypes;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface ChatActivities {
    @ActivityMethod
    public String chat(ArrayList<DataTypes.MessageRecord> history, String context);

    @ActivityMethod
    public ScoredPoint search(List<Float> search);

    @ActivityMethod
    public List<Float> embedding(String text);
}