package bitovi;

import software.amazon.awssdk.core.document.Document;

public class DebugClient {
    public static void main(String[] args) {
        Document doc = Document.fromString(
                "{\"zipCode\": 94102}");

        System.out.println("toString(): " + doc.toString());
        System.out.println("asString(): " + doc.asString());

        Document doc2 = Document.fromString(doc.asString());
        System.out.println("doc2.toString(): " + doc2.toString());
        System.out.println("doc2.asString(): " + doc2.asString());

        Document doc3 = Document.fromString(doc.toString());
        System.out.println("doc3.toString(): " + doc3.toString());
        System.out.println("doc3.asString(): " + doc3.asString());
    }
}
