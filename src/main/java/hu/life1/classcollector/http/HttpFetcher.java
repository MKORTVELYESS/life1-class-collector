package hu.life1.classcollector.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/** Thin HTTP GET helper shared by timetable sources. */
public final class HttpFetcher {

    private final HttpClient client;
    private final String userAgent;

    public HttpFetcher(HttpClient client) {
        this(client, "life1-class-collector/1.0 (+https://github.com/MKORTVELYESS/life1-class-collector)");
    }

    public HttpFetcher(HttpClient client, String userAgent) {
        this.client = Objects.requireNonNull(client, "client");
        this.userAgent = Objects.requireNonNull(userAgent, "userAgent");
    }

    public static HttpFetcher createDefault() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        return new HttpFetcher(client);
    }

    public String getText(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(90))
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/json;q=0.9,*/*;q=0.8")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int code = response.statusCode();
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + " for " + url);
        }
        return response.body();
    }
}
