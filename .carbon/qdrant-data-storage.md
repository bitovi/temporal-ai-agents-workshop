```java
public void insertEmbedding(UUID uuid, List<Float> vectorData, 
                            String payload, String source) {
    QdrantClient client = getQdrantClient();
    PointId pointId = id(uuid);
    PointStruct ps = PointStruct.newBuilder()
            .setId(pointId)
            .setVectors(vectors(vectorData))
            .putAllPayload(Map.of(
                    "payload", value(payload),
                    "source", value(source),
                    "uuid", value(uuid.toString())
            )).build();

    UpdateResult updateResult = client.upsertAsync(COLLECTION_NAME, List.of(ps)).get();
}
```
