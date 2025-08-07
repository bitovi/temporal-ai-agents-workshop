package bitovi.common;

import java.util.Collections;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.common.converter.CodecDataConverter;
import io.temporal.common.converter.DefaultDataConverter;
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
				// Use CodecDataConverter to add custom payload codec, as an example
				.setDataConverter(
						new CodecDataConverter(
								DefaultDataConverter.newDefaultInstance(),
								Collections.singletonList(new ExamplePayloadCodec())))
				.build();

		WorkflowClient client = WorkflowClient.newInstance(service, clientOptions);
		return client;
	}
}
