package bitovi;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

public class EnvironmentSetupWorker {
	private static Properties properties;

	public static void main(String[] args) {
		properties = new Properties();
		try (InputStream input = new FileInputStream("config.properties")) {
			properties.load(input);
		} catch (IOException ex) {
			ex.printStackTrace();
		}

		try {
			String temporalHostAndPort = properties.getProperty("TEMPORAL_HOST");
			String temporalNamespace = properties.getProperty("TEMPORAL_NAMESPACE");
			String apiKey = properties.getProperty("TEMPORAL_API_KEY");

			WorkflowServiceStubsOptions serviceOptions = WorkflowServiceStubsOptions
					.newBuilder()
					.setTarget(temporalHostAndPort)
					.setEnableHttps(true)
					.addApiKey(() -> apiKey)
					.build();

			WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(serviceOptions);
			WorkflowClientOptions clientOptions = WorkflowClientOptions.newBuilder()
					.setNamespace(temporalNamespace)
					.build();

			WorkflowClient client = WorkflowClient.newInstance(service, clientOptions);

			WorkerFactory factory = WorkerFactory.newInstance(client);

			Worker worker = factory.newWorker("bitovi-ai-agents-workshop");

			factory.start();

			System.out.println("Temporal Worker started successfully!");
		} catch (Exception ex) {
			System.err.println("Failed to start Temporal Worker: " + ex.getMessage());
			ex.printStackTrace();
			System.exit(1);
		}
	}
}
