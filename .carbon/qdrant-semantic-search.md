```java
public String[] searchVectorDatabase(List<Float> vector, Integer limit) {
    QdrantClient client = getQdrantClient();
    List<Points.ScoredPoint> points = client
            .searchAsync(SearchPoints.newBuilder()
                        .setCollectionName(COLLECTION_NAME)
                        .addAllVector(vector)
                        .setLimit(limit)
                        .build())
            .get();

    String[] uuids = new String[points.size()];
    for (int i = 0; i < points.size(); i++) {
        var payload = points.get(i);
        uuids[i] = payload.getId().getUuid();
    }
    return uuids;
}
```
