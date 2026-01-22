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

		WorkflowServiceStubsOptions serviceOptions = WorkflowServiceStubsOptions
				.newBuilder()
				.setTarget(temporalHostAndPort)
				.build();

		WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(serviceOptions);
		WorkflowClientOptions clientOptions = WorkflowClientOptions.newBuilder()
				.setNamespace(temporalNamespace)
				.build();

		WorkflowClient client = WorkflowClient.newInstance(service, clientOptions);
		return client;
	}
}
