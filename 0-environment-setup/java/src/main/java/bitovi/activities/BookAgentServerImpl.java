package bitovi.activities;

import bitovi.common.Config;
import io.temporal.failure.ApplicationFailure;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class BookAgentServerImpl implements BookAgentServer {

	@Override
	public String checkBookAgentServerConnection() throws ApplicationFailure {
		Config config = new Config();
		String baseUrl = config.getProperty("BOOK_AGENT_SERVER_BASE_URL");

		try {
			HttpClient httpClient = HttpClient.newHttpClient();
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(baseUrl + "/.well-known/agent-card.json"))
					.GET()
					.build();

			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw ApplicationFailure.newNonRetryableFailure(
						"Book Agent Server health check failed with status: " + response.statusCode(),
						"BookAgentServerError");
			}
		} catch (ApplicationFailure e) {
			throw e;
		} catch (Exception e) {
			throw ApplicationFailure.newNonRetryableFailure(
					"Failed to connect to Book Agent Server: " + e.getMessage(),
					"BookAgentServerError");
		}

		return "Book Agent Server connection successful.";
	}
}
