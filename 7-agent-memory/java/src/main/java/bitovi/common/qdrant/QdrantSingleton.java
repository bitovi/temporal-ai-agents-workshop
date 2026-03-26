package bitovi.common.qdrant;

import bitovi.common.Config;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;

public class QdrantSingleton {
    private QdrantSingleton() {
    }

    private static class Holder {
        static final QdrantGrpcClient GRPC_CLIENT;
        static final QdrantClient CLIENT;

        static {
            Config config = new Config();
            String host = config.getProperty("QDRANT_HOST");
            int port = config.getIntegerProperty("QDRANT_PORT_GRPC");

            GRPC_CLIENT = QdrantGrpcClient.newBuilder(host, port, false).build();
            CLIENT = new QdrantClient(GRPC_CLIENT);
        }
    }

    public static QdrantClient getClient() {
        return Holder.CLIENT;
    }

    public static void shutdown() {
        try {
            Holder.GRPC_CLIENT.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}