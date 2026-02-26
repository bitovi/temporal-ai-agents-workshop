package bitovi.common;

import java.util.Properties;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class Config {
	private static Properties properties;

	public Config() {
		properties = new Properties();
		try {
			InputStream input = new FileInputStream(".env");
			properties.load(input);
		} catch (FileNotFoundException ex) {
			ex.printStackTrace();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	public String getProperty(String key) {
		return properties.getProperty(key);
	}
}
