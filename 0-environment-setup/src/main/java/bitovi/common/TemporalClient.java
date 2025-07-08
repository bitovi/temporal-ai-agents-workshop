package bitovi.common;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

public class TemporalClient {
	public static WorkflowClient getTemporalClient() {
		Config config = new Config();
		String temporalHostAndPort = config.getProperty("TEMPORAL_HOST_PORT");
		String temporalNamespace = config.getProperty("TEMPORAL_NAMESPACE");
		// String temopralClientCertPath =
		// properties.getProperty("TEMPORAL_CLIENT_CERT_PATH");
		// String temporalClientKeyPath =
		// properties.getProperty("TEMPORAL_CLIENT_CERT_KEY_PATH");
		// String temporalCaCertPath = properties.getProperty("TEMPORAL_CA_CERT_PATH");
		// String apiKey = config.getProperty("TEMPORAL_API_KEY");

		// InputStream clientCert = new FileInputStream(temopralClientCertPath);
		// InputStream clientKey = new FileInputStream(temporalClientKeyPath);
		// InputStream caCert = new FileInputStream(temporalCaCertPath);

		// SslContext sslContext = GrpcSslContexts.configure(
		// SslContextBuilder.forClient()
		// .keyManager(clientCert, clientKey)
		// .trustManager(caCert))
		// .build();

		// SslContext sslContext = SimpleSslContextBuilder
		// .forPKCS8(clientCert, clientKey)
		// .forPKCS12(clientKey)
		// .build();

		WorkflowServiceStubsOptions serviceOptions = WorkflowServiceStubsOptions
				.newBuilder()
				.setTarget(temporalHostAndPort)
				// .setSslContext(sslContext)
				// .setEnableHttps(true)
				// .addApiKey(() -> apiKey)
				.build();

		WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(serviceOptions);
		WorkflowClientOptions clientOptions = WorkflowClientOptions.newBuilder()
				.setNamespace(temporalNamespace)
				.build();

		WorkflowClient client = WorkflowClient.newInstance(service, clientOptions);
		return client;
	}
}
