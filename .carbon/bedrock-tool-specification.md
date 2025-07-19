```java
Map<String, Document> numPropertyMap = new HashMap<>();
numPropertyMap.put("type", Document.fromString("number"));
numPropertyMap.put("description", Document.fromString("The zip code to fetch the weather for."));

Map<String, Document> propertiesMap = new HashMap<>();
propertiesMap.put("zipCode", Document.fromMap(numPropertyMap));

List<Document> requiredList = new ArrayList<>();
requiredList.add(Document.fromString("zipCode"));

Map<String, Document> rootMap = new HashMap<>();
rootMap.put("type", Document.fromString("object"));
rootMap.put("properties", Document.fromMap(propertiesMap));
rootMap.put("required", Document.fromList(requiredList));

// Now create the Document representing the JSON schema
Document document = Document.fromMap(rootMap);
```