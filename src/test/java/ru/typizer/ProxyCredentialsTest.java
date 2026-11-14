package ru.typizer;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import ru.typizer.http.HttpRequest;
import ru.typizer.http.HttpResponse;
import ru.typizer.http.UrlConnectionTransport;

/** Proxy credentials must never reach the target server as a request header. */
class ProxyCredentialsTest {
    @Test
    void credentialsAreNotSentToTheTargetWhenTheProxyIsNotAnExplicitHttpProxy() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            seen.set(ex.getRequestHeaders().getFirst("Proxy-Authorization"));
            byte[] body = "{}".getBytes("UTF-8");
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        try {
            // A direct connection with credentials configured: nothing may leak to the target.
            UrlConnectionTransport t = UrlConnectionTransport.builder()
                    .proxy(Proxy.NO_PROXY)
                    .proxyCredentials("corp-user", "corp-secret")
                    .build();
            HttpRequest req = new HttpRequest("GET",
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/me"),
                    Collections.<String, String>emptyMap(), null, Duration.ofSeconds(5), Duration.ofSeconds(5));
            boolean java8 = System.getProperty("java.specification.version").startsWith("1.");
            if (java8) {
                // No per-connection authenticator on Java 8: refuse rather than leak.
                assertThrows(IOException.class, () -> t.execute(req));
            } else {
                HttpResponse r = t.execute(req);
                r.body().close();
                assertNull(seen.get(), "Proxy-Authorization reached the target server");
            }
        } finally {
            server.stop(0);
        }
    }
}
