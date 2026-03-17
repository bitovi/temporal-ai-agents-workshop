package bitovi.activities;

import bitovi.common.Config;
import io.temporal.failure.ApplicationFailure;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class SupportAgentServerImpl implements SupportAgentServer {

	@Override
	public String checkSupportAgentServerConnection() throws ApplicationFailure {
		Config config = new Config();
		String baseUrl = config.getProperty("SUPPORT_AGENT_SERVER_BASE_URL");

		try {
			HttpClient httpClient = HttpClient.newHttpClient();
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(baseUrl + "/.well-known/agent-card.json"))
					.GET()
					.build();

			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw ApplicationFailure.newNonRetryableFailure(
						"Support Agent Server health check failed with status: " + response.statusCode(),
						"SupportAgentServerError");
			}
		} catch (ApplicationFailure e) {
			throw e;
		} catch (Exception e) {
			throw ApplicationFailure.newNonRetryableFailure(
					"Failed to connect to Support Agent Server: " + e.getMessage(),
					"SupportAgentServerError");
		}

		return "Support Agent Server connection successful.";
	}
}
