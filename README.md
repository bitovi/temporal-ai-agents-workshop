# Temporal Java Notes

## How to Install the Java SDK
You can add the Temporal SDK to your Java projects by adding the following dependency to your pom.xml:

```xml
       <dependency>
            <groupId>io.temporal</groupId>
            <artifactId>temporal-sdk</artifactId>
            <version>1.19.1</version>
        </dependency>
```

## Create a new Maven Project
```
mvn -B archetype:generate -DgroupId=bitovi -DartifactId=1-intro-to-ai-agents -DarchetypeArtifactId=maven-archetype-quickstart -DarchetypeVersion=1.4
```

This results in a new `1-intro-to-ai-agents` directory with a pom.xml and src directory, App.java and AppTest.java files.


Default pom.xml was wrong and needed to be updated to Java 8+

```xml
  <properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <maven.compiler.source>8</maven.compiler.source>
    <maven.compiler.target>8</maven.compiler.target>
  </properties>
```


Defining a Java Interface as a WorkflowInterface is done using a decorator, @WorkflowInterface and one of the methods within it as a WorkflowMethod by the @WorkflowMethod decorator

```java
package main.java.bitovi;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface Demo {
    
    @WorkflowMethod
    /**
     * A simple workflow method that greets someone by name.
     *
     * @param name the name of the person to greet
     * @return a greeting message
     */
    String greetSomeone(String name);

}

```
