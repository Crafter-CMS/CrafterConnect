package net.crafter.plugin.core.connect;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** HTTP request (tests use a fake implementation; no real network access) */
public interface HttpTransport {

    record Response(int status, String body) {}

    Response send(String method, URI uri, Map<String, String> headers, String jsonBody) throws IOException;

    /** Default implementation using java.net.http */
    static HttpTransport standard(String userAgent) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return (method, uri, headers, body) -> {
            HttpRequest.Builder b = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .header("User-Agent", userAgent);
            headers.forEach(b::header);
            if (body != null) {
                b.header("Content-Type", "application/json");
                b.method(method, HttpRequest.BodyPublishers.ofString(body));
            } else {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            }
            try {
                HttpResponse<String> res = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
                return new Response(res.statusCode(), res.body());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", e);
            }
        };
    }
}
