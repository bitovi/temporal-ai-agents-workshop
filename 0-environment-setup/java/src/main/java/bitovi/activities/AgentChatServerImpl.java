package bitovi.activities;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import bitovi.common.Config;
import io.temporal.failure.ApplicationFailure;

public class AgentChatServerImpl implements AgentChatServer {

	@Override
	public String checkAgentChatServerConnection() throws ApplicationFailure {
		Config config = new Config();
		String baseUrl = config.getProperty("AGENT_CHAT_SERVER_BASE_URL");

		try {
			HttpClient httpClient = HttpClient.newHttpClient();
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(baseUrl + "/"))
					.GET()
					.build();

			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				System.err.println("Agent Chat Server health check failed with status: " + response.statusCode());
				throw ApplicationFailure.newNonRetryableFailure(
						"Agent Chat Server health check failed with status: " + response.statusCode(),
						"AgentChatServerError");
			}
		} catch (ApplicationFailure e) {
			System.err.println(e.getMessage());
			throw e;
		} catch (Exception e) {
			System.err.println("Error connecting to Agent Chat Server: " + e.getMessage());
			throw ApplicationFailure.newNonRetryableFailure(
					"Failed to connect to Agent Chat Server: " + e.getMessage(),
					"AgentChatServerError");
		}

		return "Agent Chat Server connection successful.";
	}
}
