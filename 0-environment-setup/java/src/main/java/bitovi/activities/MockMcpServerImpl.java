package bitovi.activities;

import bitovi.common.Config;
import io.temporal.failure.ApplicationFailure;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class MockMcpServerImpl implements MockMcpServer {

	@Override
	public String checkMockMcpServerConnection() throws ApplicationFailure {
		Config config = new Config();
		String baseUrl = config.getProperty("MCP_SERVER_BASE_URL");

		try {
			HttpClient httpClient = HttpClient.newHttpClient();
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(baseUrl + "/readyz"))
					.GET()
					.build();

			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw ApplicationFailure.newNonRetryableFailure(
						"Mock MCP Server health check failed with status: " + response.statusCode(),
						"MockMcpServerError");
			}
		} catch (ApplicationFailure e) {
			throw e;
		} catch (Exception e) {
			throw ApplicationFailure.newNonRetryableFailure(
					"Failed to connect to Mock MCP Server: " + e.getMessage(),
					"MockMcpServerError");
		}

		return "Mock MCP Server connection successful.";
	}
}
