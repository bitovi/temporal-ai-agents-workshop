package bitovi.common.local.types;

import static io.qdrant.client.ConditionFactory.matchKeyword;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.WithPayloadSelectorFactory.enable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.qdrant.client.grpc.Common.Filter;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.ScrollPoints;

public record UserPreferenceMemoryRecord(UUID id, String context, String preference, String[] categories) {

    public static UserPreferenceMemoryRecord fromJson(JSONObject obj, UUID id) throws JSONException {
        String context = obj.getString("context");
        String preference = obj.getString("preference");
        JSONArray categoriesArray = obj.getJSONArray("categories");
        String[] categories = new String[categoriesArray.length()];
        for (int j = 0; j < categoriesArray.length(); j++) {
            categories[j] = categoriesArray.getString(j);
        }

        return new UserPreferenceMemoryRecord(id, context, preference, categories);
    }

    public static UserPreferenceMemoryRecord fromJson(JSONObject obj) throws JSONException {
        return fromJson(obj, UUID.randomUUID());
    }

    public String toRelationXML(List<UserPreferenceMemoryRecord> memories) {
        StringBuilder sb = new StringBuilder();
        sb.append("<memory>\n");
        sb.append("  <context>").append(this.context()).append("</context>\n");
        sb.append("  <preference>").append(this.preference()).append("</preference>\n");
        sb.append("  <categories>").append(String.join(",", this.categories())).append("</categories>\n");
        sb.append("  <relatedMemories>\n");
        for (UserPreferenceMemoryRecord related : memories) {
            sb.append("    <memory>\n");
            sb.append("      <id>").append(related.id()).append("</id>\n");
            sb.append("      <context>").append(related.context()).append("</context>\n");
            sb.append("      <preference>").append(related.preference()).append("</preference>\n");
            sb.append("      <categories>").append(String.join(",", related.categories())).append("</categories>\n");
            sb.append("    </memory>\n");
        }
        sb.append("  </relatedMemories>\n");
        sb.append("</memory>");
        return sb.toString();
    }

    @SuppressWarnings("null")
    public Map<String, Value> vectorPayload() {
        return Map.of(
                "preference", value(this.preference()),
                "categories", value(Arrays.toString(this.categories())),
                "uuid", value(this.id().toString()),
                "context", value(this.context()),
                "memory_type", value("user_preference_memory"));
    }

    public static ScrollPoints vectorScrollSearch() {
        return ScrollPoints.newBuilder()
                .setFilter(
                        Filter.newBuilder()
                                .addAllShould(
                                        List.of(matchKeyword("memory_type", "user_preference_memory")))
                                .build())
                .setWithPayload(enable(true))
                .build();
    }

    public static UserPreferenceMemoryRecord fromScoredPoint(Points.ScoredPoint point) throws JSONException {
        return fromMap(point.getPayloadMap());
    }

    public static UserPreferenceMemoryRecord fromRetrievedPoint(Points.RetrievedPoint point) throws JSONException {
        return fromMap(point.getPayloadMap());
    }

    public static UserPreferenceMemoryRecord fromMap(Map<String, Value> map) throws JSONException {
        String context = map.get("context").getStringValue();
        String preference = map.get("preference").getStringValue();
        UUID id = UUID.fromString(map.get("uuid").getStringValue());

        JSONArray categoriesArray = new JSONArray(map.get("categories").getStringValue());
        String[] categories = new String[categoriesArray.length()];
        for (int j = 0; j < categoriesArray.length(); j++) {
            categories[j] = categoriesArray.getString(j);
        }

        return new UserPreferenceMemoryRecord(id, context, preference, categories);
    }
}
