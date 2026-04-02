# A2A Common Part Types

## Data / JSON Part

```java
record DataPart(Object data) implements Part<Object> {
    public static final String DATA = "data";

    public DataPart (Object data) {
        Assert.checkNotNullParam("data", data);
        this.data = data;
    }

    public static DataPart fromJson(String json) {
        Assert.checkNotNullParam("json", json);
        try {
            Object data = JSON_PARSER.fromJson(json, Object.class);
            return new DataPart(data);
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Invalid JSON: " + json, e);
        }
    }
}
```

## Text Part

```java
record TextPart(String text) implements Part<String> {
    public static final String TEXT = "text";

    public TextPart (String text) {
        Assert.checkNotNullParam("text", text);
        this.text = text;
    }
}
```

## File Part

```java
record FilePart(FileContent file) implements Part<FileContent> {
    public static final String FILE = "file";

    public FilePart (FileContent file) {
        Assert.checkNotNullParam("file", file);
        this.file = file;
    }
}

record FileContent(String mimeType, String name, ByteSource source) {
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB
    // In the A2A SDK there is a lot more checking here, conversaion to base64, etc
}
```
