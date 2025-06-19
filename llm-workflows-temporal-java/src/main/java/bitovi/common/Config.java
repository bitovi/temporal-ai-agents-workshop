package bitovi.common;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Properties;

import io.temporal.activity.ActivityOptions;

public class Config {
    private static Properties properties = null;

    public static final int MAX_TURNS_BEFORE_CONTINUE = 250; // Maximum turns before continuing the workflow.

    public static ActivityOptions getDefaultActivityOptions() {
        ActivityOptions defaulActivityOptions = ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(120))
                .build();

        return defaulActivityOptions;
    }

    public static Properties getProperties() {
        if (properties == null) {
            properties = new Properties();

            // Try loading from classpath first (recommended for packaged apps)
            try (InputStream input = Config.class.getClassLoader().getResourceAsStream("config.properties")) {
                if (input != null) {
                    properties.load(input);
                    System.out.println("Loaded properties from classpath: config.properties");
                    return properties;
                }
            } catch (IOException e) {
                System.err.println("Error loading properties from classpath: " + e.getMessage());
            }

            // Fallback to file system
            try {
                String path = System.getProperty("config.file", "config.properties");
                System.out.println("Loading properties from file system: " + path);
                try (FileInputStream input = new FileInputStream(path)) {
                    properties.load(input);
                }
            } catch (IOException e) {
                System.err.println("Error loading properties file: " + e.getMessage());
            }
        }

        return properties;
    }

    public static String getProperty(String key) {
        Properties properties = getProperties();
        return properties.getProperty(key);
    }

    public static String getProperty(String key, String defaultValue) {
        return getProperties().getProperty(key, defaultValue);
    }

    public static String readFile(String file) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line = null;
        StringBuilder stringBuilder = new StringBuilder();
        String ls = System.getProperty("line.separator");

        try {
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
                stringBuilder.append(ls);
            }

            return stringBuilder.toString();
        } finally {
            reader.close();
        }
    }

}
