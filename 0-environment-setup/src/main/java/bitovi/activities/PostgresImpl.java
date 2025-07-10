package bitovi.activities;

import io.temporal.failure.ApplicationFailure;
import bitovi.common.Config;

public class PostgresImpl implements Postgres {

	@Override
	public void checkPostgresConnection() throws ApplicationFailure {
		Config config = new Config();
		String POSTGRES_JDBC_CONNECTION_STRING = config.getProperty("POSTGRES_JDBC_CONNECTION_STRING");

	}
}