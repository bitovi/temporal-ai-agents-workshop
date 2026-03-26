package bitovi.common.local.types;

import static io.qdrant.client.ConditionFactory.matchKeyword;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.WithPayloadSelectorFactory.enable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.json.JSONException;
import org.json.JSONObject;

import io.qdrant.client.grpc.Common.Filter;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.SearchPoints;

public record SemanticMemoryRecord(UUID id, String fact) {

    private static final String MEMORY_TYPE = "semantic_memory";
    private static final String MEMORY_TYPE_FIELD = "memory_type";

    public static SemanticMemoryRecord fromJson(JSONObject obj, UUID id) throws JSONException {
        String fact = obj.getString("fact");
        return new SemanticMemoryRecord(id, fact);
    }

    public static SemanticMemoryRecord fromJson(JSONObject obj) throws JSONException {
        return fromJson(obj, UUID.randomUUID());
    }

    public String toRelationXML(List<SemanticMemoryRecord> memories) {
        StringBuilder sb = new StringBuilder();
        sb.append("<memory>\n");
        sb.append("  <fact>").append(this.fact()).append("</fact>\n");
        sb.append("  <relatedMemories>\n");
        for (SemanticMemoryRecord related : memories) {
            sb.append("    <memory>\n");
            sb.append("      <id>").append(related.id()).append("</id>\n");
            sb.append("      <fact>").append(related.fact()).append("</fact>\n");
            sb.append("    </memory>\n");
        }
        sb.append("  </relatedMemories>\n");
        sb.append("</memory>");
        return sb.toString();
    }

    @SuppressWarnings("null")
    public Map<String, Value> vectorPayload() {
        return Map.of(
                "fact", value(this.fact()),
                "uuid", value(this.id().toString()),
                MEMORY_TYPE_FIELD, value(MEMORY_TYPE));
    }

    public static SearchPoints vectorSearchAsync(List<Float> vector, int topK, float threshold) {
        return SearchPoints.newBuilder()
                .addAllVector(vector)
                .setLimit(topK)
                .setFilter(
                        Filter.newBuilder()
                                .addAllShould(
                                        List.of(matchKeyword(MEMORY_TYPE_FIELD, MEMORY_TYPE)))
                                .build())
                .setWithPayload(enable(true))
                .build();
    }

    public static SemanticMemoryRecord fromScoredPoint(Points.ScoredPoint point) throws JSONException {
        return fromMap(point.getPayloadMap());
    }

    public static SemanticMemoryRecord fromRetrievedPoint(Points.RetrievedPoint point) throws JSONException {
        return fromMap(point.getPayloadMap());
    }

    public static SemanticMemoryRecord fromMap(Map<String, Value> map) throws JSONException {
        String fact = map.get("fact").getStringValue();
        UUID id = UUID.fromString(map.get("uuid").getStringValue());

        return new SemanticMemoryRecord(id, fact);
    }
}
