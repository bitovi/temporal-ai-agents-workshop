package bitovi.activities;

import bitovi.common.Config;
import io.temporal.failure.ApplicationFailure;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import java.util.concurrent.ExecutionException;

public class QdrantImpl implements Qdrant {

	@Override
	public void checkQdrantConnection() throws ApplicationFailure {
		Config config = new Config();

		String QDRANT_HOST = config.getProperty("QDRANT_HOST");
		String QDRANT_PORT_GRPC = config.getProperty("QDRANT_PORT_GRPC");
		QdrantClient client = null;

		try {
			client = new QdrantClient(
					QdrantGrpcClient.newBuilder(QDRANT_HOST, Integer.parseInt(QDRANT_PORT_GRPC), false)
							.build());

			client.listCollectionsAsync().get();
		} catch (InterruptedException e) {
			throw ApplicationFailure.newNonRetryableFailure(
					"Interrupted while checking Qdrant connection: " + e.getMessage(),
					"QdrantError");
		} catch (ExecutionException e) {
			throw ApplicationFailure.newNonRetryableFailure(
					"Execution failed while checking Qdrant connection: " + e.getMessage(),
					"QdrantError");
		} finally {
			if (client != null) {
				client.close();
			}
		}
	}
}