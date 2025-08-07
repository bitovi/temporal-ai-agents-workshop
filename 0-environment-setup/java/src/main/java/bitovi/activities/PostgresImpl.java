package bitovi.activities;

import io.temporal.failure.ApplicationFailure;
import bitovi.common.Config;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class PostgresImpl implements Postgres {

	@Override
	public void checkPostgresConnection() throws ApplicationFailure {
		Config config = new Config();
		String POSTGRES_HOST = config.getProperty("POSTGRES_HOST");
		String POSTGRES_PORT = config.getProperty("POSTGRES_PORT");
		String POSTGRES_DATABASE = config.getProperty("POSTGRES_DATABASE");
		String POSTGRES_USERNAME = config.getProperty("POSTGRES_USERNAME");
		String POSTGRES_PASSWORD = config.getProperty("POSTGRES_PASSWORD");
		Connection connection = null;

		try {
			Class.forName("org.postgresql.Driver");

			connection = DriverManager.getConnection(
					"jdbc:postgresql://" + POSTGRES_HOST + ":" + POSTGRES_PORT + "/" + POSTGRES_DATABASE,
					POSTGRES_USERNAME,
					POSTGRES_PASSWORD);

			if (connection != null) {
				System.out.println("Connected to Postgres successfully.");
			} else {
				throw ApplicationFailure.newNonRetryableFailure("Failed to connect to Postgres. Connection is null.",
						"PostgresConnectionError");
			}
		} catch (ClassNotFoundException e) {
			throw ApplicationFailure.newNonRetryableFailure("PostgreSQL JDBC driver not found: " + e.getMessage(),
					"PostgresDriverNotFoundError");
		} catch (SQLException e) {
			throw ApplicationFailure.newNonRetryableFailure("Connection to database failed: " + e.getMessage(),
					"PostgresConnectionError");
		} finally {
			if (connection != null) {
				try {
					connection.close();
				} catch (Exception e) {
					System.err.println("Error closing Postgres connection: " + e.getMessage());
				}
			}
		}
	}
}