package bitovi.common;

import java.util.Properties;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;

public class Config {
	private static Properties properties;

	public Config() {
		properties = new Properties();
		try (InputStream input = new FileInputStream("config.properties")) {
			properties.load(input);
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	public String getProperty(String key) {
		return properties.getProperty(key);
	}
}
