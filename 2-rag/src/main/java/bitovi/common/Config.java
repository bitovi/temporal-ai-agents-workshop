package bitovi.common;

import java.util.Properties;
import java.io.IOException;

public class Config {
	private static Properties properties;

	public Config() {
		properties = new Properties();
		try {
			properties.load(getClass().getClassLoader().getResourceAsStream("config.properties"));
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	public String getProperty(String key) {
		return properties.getProperty(key);
	}

	public Integer getIntegerProperty(String key) {
		String value = properties.getProperty(key);
		if (value != null) {
			try {
				return Integer.parseInt(value);
			} catch (NumberFormatException e) {
				e.printStackTrace();
			}
		}
		return null;
	}
}
