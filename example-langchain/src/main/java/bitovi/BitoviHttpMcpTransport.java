package bitovi;

import java.time.Duration;

import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import okhttp3.OkHttpClient;

public class BitoviHttpMcpTransport extends HttpMcpTransport {

    private OkHttpClient overrideClient;

    public BitoviHttpMcpTransport(Builder builder, OkHttpClient client) {
        super(builder);

        this.overrideClient = client;
    }

}
