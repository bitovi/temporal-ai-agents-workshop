package bitovi.common;

import java.util.Properties;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;

public class Config {
	private static Properties properties;

	public Config() {
		properties = new Properties();
		try (InputStream input = new FileInputStream(".env")) {
			properties.load(input);
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	public String getProperty(String key) {
		return properties.getProperty(key);
	}

	public int getIntegerProperty(String key) {
		String value = properties.getProperty(key);
		if (value == null || value.isEmpty()) {
			throw new IllegalArgumentException(
					"Property '" + key + "' is not defined or is empty in the configuration.");
		}
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Property '" + key + "' is not a valid integer.");
		}
	}

	public boolean getBooleanProperty(String key) {
		String value = properties.getProperty(key);
		if (value == null || value.isEmpty()) {
			return false;
		}
		return Boolean.parseBoolean(value);

	}
}
