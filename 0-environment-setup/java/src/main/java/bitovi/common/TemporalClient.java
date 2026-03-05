package bitovi.common;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import javax.net.ssl.SSLException;

import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.SimpleSslContextBuilder;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

public class TemporalClient {
	public static WorkflowClient getTemporalClient() throws SSLException, FileNotFoundException {
		Config config = new Config();
		String temporalHostAndPort = config.getProperty("TEMPORAL_HOST_PORT");
		String temporalNamespace = config.getProperty("TEMPORAL_NAMESPACE");

		String clientCertPath = config.getProperty("TEMPORAL_CLIENT_CERTIFICATE_PATH");
		String clientKeyPath = config.getProperty("TEMPORAL_CLIENT_KEY_PATH");

		WorkflowServiceStubsOptions serviceOptions;

		if (clientCertPath == null || clientKeyPath == null) {
			// Options for insecure connection (usually Local Temporal Server)
			serviceOptions = WorkflowServiceStubsOptions
					.newBuilder()
					.setTarget(temporalHostAndPort)
					.build();
		} else {
			// Options for mTLS connection (usually Temporal Cloud)
			InputStream clientCertInputStream = new FileInputStream(clientCertPath);
			InputStream clientKeyInputStream = new FileInputStream(clientKeyPath);
			SslContext sslContext = SimpleSslContextBuilder.forPKCS8(clientCertInputStream, clientKeyInputStream)
					.build();

			serviceOptions = WorkflowServiceStubsOptions
					.newBuilder()
					.setTarget(temporalHostAndPort)
					.setSslContext(sslContext)
					.build();
		}

		WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(serviceOptions);
		WorkflowClientOptions clientOptions = WorkflowClientOptions.newBuilder()
				.setNamespace(temporalNamespace)
				.build();

		WorkflowClient client = WorkflowClient.newInstance(service, clientOptions);
		return client;
	}
}
