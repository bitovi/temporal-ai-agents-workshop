```java
private void createCollection(String collectionName){
    QdrantClient client = getQdrantClient();
    CollectionOperationResponse result = client.createCollectionAsync(collectionName,
            VectorParams.newBuilder()
                    .setDistance(Distance.Cosine)
                    .setSize(COLLECTION_VECTOR_SIZE)
                    .build())
            .get();
    if (result.getResult()) {
        System.out.println("Collection '" + collectionName + "' created successfully.");
    } else {
        System.err.println("Failed to create collection '" + collectionName + "'.");
    }
}
```
