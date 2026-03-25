package bitovi.common;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.annotation.Nonnull;

public class Config {
	private static Properties properties;

	public Config() {
		properties = new Properties();
		try (InputStream input = new FileInputStream(".env")) {
			properties.load(input);
		} catch (IOException ex) {
			throw new IllegalArgumentException(
					"Failed to load configuration from .env file.", ex);
		}
	}

	@Nonnull
	public String getProperty(String key) {
		String temp = properties.getProperty(key);

		if (temp == null || temp.isEmpty()) {
			throw new IllegalArgumentException(
					"Property '" + key + "' is not defined or is empty in the configuration.");
		}

		return temp;
	}

	public String getNullableProperty(String key) {
		String temp = properties.getProperty(key);

		if (temp == null || temp.isEmpty()) {
			return null;
		}

		return temp;
	}

	@Nonnull
	public Integer getIntegerProperty(String key) {
		String value = properties.getProperty(key);

		if (value == null || value.isEmpty()) {
			throw new IllegalArgumentException(
					"Property '" + key + "' is not defined or is empty in the configuration.");
		}
		try {
			Integer intValue = Integer.valueOf(value);
			if (intValue == null) {
				throw new IllegalArgumentException(
						"Property '" + key + "' is not a valid integer.");
			}
			return intValue;
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(
					"Property '" + key + "' is not a valid integer.");
		}
	}
}
