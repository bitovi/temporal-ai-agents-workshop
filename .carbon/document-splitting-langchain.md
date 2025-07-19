```java
public void embed(String storageKey, UUID uuid) {
    String outputPath = System.getProperty("java.io.tmpdir") + "/" + uuid.toString() + ".txt";
    Path path = AWS.downloadFile(bucketName, storageKey, outputPath);

    String content = Files.readString(path, StandardCharsets.UTF_8);
    DocumentByParagraphSplitter splitter = new DocumentByParagraphSplitter(2500, 500);
    String[] chunks = splitter.split(content);

    for (String chunk : chunks) {
        List<Float> embedding = AWS.calculateEmbedding(chunk);
        VectorDatabaseClient.insertEmbedding(uuid, embedding, chunk, storageKey);
    }
}
```
