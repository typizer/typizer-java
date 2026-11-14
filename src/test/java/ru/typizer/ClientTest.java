package ru.typizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.typizer.errors.ConfigurationException;
import ru.typizer.errors.ConnectionException;
import ru.typizer.errors.RateLimitException;
import ru.typizer.http.HttpRequest;
import ru.typizer.http.HttpResponse;
import ru.typizer.http.HttpTransport;
import ru.typizer.internal.Json;
import ru.typizer.models.Run;
import ru.typizer.models.Typizer;

/** The client without a server: configuration, headers, retries, raw responses, models. */
class ClientTest {
    /** Answers from a script and records what was sent. */
    static final class FakeTransport implements HttpTransport {
        final Deque<Object> script = new ArrayDeque<>();
        final List<HttpRequest> sent = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();

        FakeTransport respond(int status, String body, String... headers) {
            script.add(new Object[] {status, body, headers});
            return this;
        }

        FakeTransport fail(IOException e) {
            script.add(e);
            return this;
        }

        @Override
        public HttpResponse execute(HttpRequest request) throws IOException {
            sent.add(request);
            if (request.body() != null) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                request.body().writeTo(out);
                bodies.add(new String(out.toByteArray(), StandardCharsets.UTF_8));
            } else {
                bodies.add(null);
            }
            Object next = script.poll();
            if (next instanceof IOException) throw (IOException) next;
            Object[] r = (Object[]) next;
            Map<String, List<String>> headers = new LinkedHashMap<>();
            String[] h = (String[]) r[2];
            for (int i = 0; i < h.length; i += 2) headers.put(h[i], Collections.singletonList(h[i + 1]));
            return new HttpResponse((Integer) r[0], headers, new ByteArrayInputStream(((String) r[1]).getBytes(StandardCharsets.UTF_8)), null);
        }
    }

    static TypizerClient client(FakeTransport t) {
        return TypizerClient.builder().apiKey("tz_live_secretsecret1234").baseUrl("https://api.example.ru/v1/").httpClient(t)
                .logger(TypizerLogger.none()).build();
    }

    static final String RUN = "{\"id\":\"run_1\",\"object\":\"run\",\"status\":\"succeeded\",\"output\":{\"invoice_number\":\"А-1\",\"total\":10.50,\"extra\":true}}";

    @Test
    void keyIsRequiredAtConstruction() {
        ConfigurationException e = assertThrows(ConfigurationException.class, () -> TypizerClient.builder().build());
        assertTrue(e.getMessage().contains("TYPIZER_API_KEY"));
        assertThrows(ConfigurationException.class, () -> TypizerClient.builder().apiKey("tz_live_ a").build());
        assertThrows(ConfigurationException.class, () -> TypizerClient.builder().apiKey("k").baseUrl("ftp://x").build());
        assertThrows(ConfigurationException.class, () -> TypizerClient.builder().apiKey("k").proxy("https://proxy:1").build());
        assertThrows(ConfigurationException.class,
                () -> TypizerClient.builder().apiKey("k").httpClient(new FakeTransport()).proxy("http://p:1").build());
    }

    @Test
    void keyIsMaskedAndSentOnlyAsBearer() {
        FakeTransport t = new FakeTransport().respond(200, "{\"object\":\"me\"}");
        TypizerClient c = client(t);
        assertEquals("TypizerClient{baseUrl=https://api.example.ru, apiKey=tz_live_…1234, timeout=PT1M15S, maxRetries=2}",
                c.toString());
        c.me();
        HttpRequest r = t.sent.get(0);
        assertEquals("https://api.example.ru/v1/me", r.uri().toString());
        assertEquals("Bearer tz_live_secretsecret1234", r.headers().get("Authorization"));
        assertFalse(r.toString().contains("secret"));
    }

    @Test
    void userAgentAndHeaders() {
        String ua = TypizerClient.userAgent("acme-erp", "2.1.0");
        assertTrue(ua.matches("^typizer-java/\\d+\\.\\d+\\.\\d+ \\(java/[^;]+; (linux|darwin|windows|[a-z0-9-]+)/\\S+\\) acme-erp/2\\.1\\.0$"), ua);

        FakeTransport t = new FakeTransport().respond(200, "{}");
        TypizerClient c = TypizerClient.builder().apiKey("tz_test_k").baseUrl("http://localhost:1").httpClient(t)
                .defaultHeader("authorization", "Bearer stolen").defaultHeader("X-Trace", "1").logger(TypizerLogger.none()).build();
        c.balance(RequestOptions.builder().header("Authorization", "nope").header("X-Call", "2").build());
        Map<String, String> h = t.sent.get(0).headers();
        assertEquals("Bearer tz_test_k", h.get("Authorization"));
        assertEquals("1", h.get("X-Trace"));
        assertEquals("2", h.get("X-Call"));
        assertEquals(1, h.keySet().stream().filter(k -> k.equalsIgnoreCase("authorization")).count());
    }

    @Test
    void longRetryAfterFailsAtOnce() {
        FakeTransport t = new FakeTransport().respond(429,
                "{\"code\":\"rate_limited\",\"title\":\"Rate limited\",\"status\":429,\"detail\":\"slow down\",\"request_id\":\"req_1\"}",
                "Retry-After", "120");
        RateLimitException e = assertThrows(RateLimitException.class, () -> client(t).me());
        assertEquals(1, t.sent.size());
        assertEquals(120, e.retryAfter().getSeconds());
        assertEquals("req_1", e.requestId());
    }

    @Test
    void connectionErrorsAreRetriedWithTheSameKeyButPatchOnlyIfNotSent() {
        FakeTransport t = new FakeTransport().fail(new IOException("reset")).respond(201, "{\"id\":\"tz_1\",\"object\":\"typizer\"}");
        TypizerClient c = client(t).withMaxRetries(1);
        Typizer created = c.typizers().create("x", "{\"type\":\"object\",\"properties\":{}}");
        assertEquals("tz_1", created.id());
        assertEquals(2, t.sent.size());
        assertEquals(t.sent.get(0).headers().get("Idempotency-Key"), t.sent.get(1).headers().get("Idempotency-Key"));
        assertEquals(t.bodies.get(0), t.bodies.get(1));

        FakeTransport p = new FakeTransport().fail(new IOException("reset after sending"));
        assertThrows(ConnectionException.class, () -> client(p).typizers().update("tz_1", TypizerParams.builder().name("y").build()));
        assertEquals(1, p.sent.size());
        assertNull(p.sent.get(0).headers().get("Idempotency-Key"));

        FakeTransport q = new FakeTransport().fail(new ConnectException("refused")).respond(200, "{\"id\":\"tz_1\"}");
        client(q).withMaxRetries(1).typizers().update("tz_1", TypizerParams.builder().name("y").build());
        assertEquals(2, q.sent.size());
    }

    @Test
    void rawResponseAndDownloads() {
        FakeTransport t = new FakeTransport()
                .respond(200, RUN, "X-Request-Id", "req_42", "Idempotent-Replayed", "true", "RateLimit-Remaining", "7")
                .respond(200, "<Invoice/>", "Content-Type", "application/xml; charset=utf-8", "Content-Disposition",
                        "attachment; filename*=UTF-8''%D1%81%D1%87%D1%91%D1%82.xml");
        TypizerClient c = client(t);
        TypizerResponse<Run> r = c.withRawResponse(x -> x.runs().get("run_1"));
        assertEquals(200, r.statusCode());
        assertEquals("req_42", r.requestId());
        assertTrue(r.idempotentReplayed());
        assertEquals(7L, r.rateLimit().remaining().longValue());
        assertEquals("run_1", r.data().id());

        Download d = c.runs().output("run_1", ru.typizer.models.Format.XML);
        assertEquals("счёт.xml", d.filename());
        assertEquals("<Invoice/>", d.text());
        assertEquals("format=xml", t.sent.get(1).uri().getRawQuery());
    }

    public static final class Invoice {
        public String invoice_number;
        public BigDecimal total;
    }

    @Test
    void outputAsAndExactNumbers() throws Exception {
        Run run = new Run(Json.parseObject(RUN));
        assertEquals(new BigDecimal("10.50"), run.output().get("total"));
        Invoice inv = run.outputAs(Invoice.class);
        assertEquals("А-1", inv.invoice_number);
        assertEquals(0, new BigDecimal("10.5").compareTo(inv.total));
        assertTrue(run.toString().contains("\"output\":\"…\""), run.toString());

        // A Jackson tree as the schema.
        JsonNode node = new ObjectMapper().readTree("{\"type\":\"object\",\"properties\":{\"b\":{\"type\":\"string\"},\"a\":{\"type\":\"number\"}}}");
        ExtractParams p = ExtractParams.builder().url("https://x").schema(node).build();
        assertEquals(Arrays.asList("b", "a"), new ArrayList<>(ContractTest.obj(p.schema.get("properties")).keySet()));
    }

    @Test
    void jsonCodec() {
        Map<String, Object> m = Json.parseObject("{\"z\":1,\"a\":[true,null,-0.5,1e3,12345678901234567890],\"s\":\"\\u0410\\ud83d\\ude00\\n\\\"\"}");
        assertEquals(Arrays.asList("z", "a", "s"), new ArrayList<>(m.keySet()));
        assertEquals(1L, m.get("z"));
        List<?> a = (List<?>) m.get("a");
        assertEquals(Boolean.TRUE, a.get(0));
        assertNull(a.get(1));
        assertEquals(new BigDecimal("-0.5"), a.get(2));
        assertEquals(new BigDecimal("1e3"), a.get(3));
        assertEquals(new java.math.BigInteger("12345678901234567890"), a.get(4));
        assertEquals("А\uD83D\uDE00\n\"", m.get("s"));
        assertEquals("{\"z\":1,\"a\":[true,null,-0.5,1E+3,12345678901234567890],\"s\":\"А\uD83D\uDE00\\n\\\"\"}", Json.write(m));
        assertEquals("\"\\u0001\\t\"", Json.write("\u0001\t"));
        for (String bad : new String[] {"", "{", "{\"a\":}", "[1,]", "01", "\"\\x\"", "{} x", "nul", "1.", "-"}) {
            assertThrows(IllegalArgumentException.class, () -> Json.parse(bad), bad);
        }
    }

    @Test
    void catalogReferences() {
        CatalogService.Ref r = CatalogService.Ref.parse("acme/akt-sverki@1.2.0", null);
        assertEquals("acme", r.publisher);
        assertEquals("akt-sverki", r.slug);
        assertEquals("1.2.0", r.version);
        CatalogService.Ref o = CatalogService.Ref.parse("schet-faktura", "1");
        assertNull(o.publisher);
        assertEquals("1", o.version);
        assertThrows(IllegalArgumentException.class, () -> CatalogService.Ref.parse("a/b/c", null));
        assertThrows(IllegalArgumentException.class, () -> CatalogService.Ref.parse("x@1", "2"));
        assertEquals("acme%2Fakt-sverki%20%D0%90", Core.encode("acme/akt-sverki А"));
    }

    @Test
    void backoffIsWithinSpec() {
        for (int n = 0; n < 6; n++) {
            double base = Math.min(8.0, 0.5 * Math.pow(2, n));
            for (int i = 0; i < 50; i++) {
                long ms = Core.backoff(n).toMillis();
                assertTrue(ms >= (long) (base * 750) - 1 && ms <= (long) (base * 1000), n + ": " + ms);
            }
        }
    }

    /**
     * The real API answers 401 with {@code WWW-Authenticate: Bearer}; HttpURLConnection treats that
     * specially for request bodies sent in streaming mode. The error must still arrive as an
     * {@code AuthenticationException} with its request id, after one request.
     */
    @Test
    void unauthorizedWithChallengeOnPostAndUpload() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0), 0);
        java.util.concurrent.atomic.AtomicInteger hits = new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/", ex -> {
            hits.incrementAndGet();
            byte[] in = new byte[8192];
            while (ex.getRequestBody().read(in) != -1) {
                // drain
            }
            byte[] body = ("{\"type\":\"https://docs.typizer.ru/errors/unauthorized\",\"title\":\"Unauthorized\",\"status\":401,"
                    + "\"detail\":\"Invalid API key\",\"code\":\"unauthorized\",\"request_id\":\"req_401\","
                    + "\"doc_url\":\"https://docs.typizer.ru/errors/unauthorized\"}").getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("WWW-Authenticate", "Bearer realm=\"typizer\", error=\"invalid_token\"");
            ex.getResponseHeaders().add("Content-Type", "application/problem+json");
            ex.getResponseHeaders().add("X-Request-Id", "req_401");
            ex.sendResponseHeaders(401, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        try {
            TypizerClient c = TypizerClient.builder().apiKey("tz_live_bad").logger(TypizerLogger.none())
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).build();
            ru.typizer.errors.AuthenticationException json = assertThrows(ru.typizer.errors.AuthenticationException.class,
                    () -> c.typizers().create("x", "{\"type\":\"object\",\"properties\":{}}"));
            assertEquals("req_401", json.requestId());
            ru.typizer.errors.AuthenticationException file = assertThrows(ru.typizer.errors.AuthenticationException.class,
                    () -> c.files().upload(TypizerFile.fromBytes(new byte[100_000], "a.pdf")));
            assertEquals("unauthorized", file.code());
            ru.typizer.errors.AuthenticationException patch = assertThrows(ru.typizer.errors.AuthenticationException.class,
                    () -> c.typizers().update("tz_1", TypizerParams.builder().name("y").build()));
            assertEquals("unauthorized", patch.code());
            assertThrows(ru.typizer.errors.AuthenticationException.class, c::me);
            assertEquals(4, hits.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void retryableFlags() {
        assertTrue(ru.typizer.errors.ApiException.from(408, null, "{\"code\":\"invalid_request\"}").isRetryable());
        assertInstanceOf(ru.typizer.errors.ServiceUnavailableException.class, ru.typizer.errors.ApiException.from(502, null, "<html>"));
        assertInstanceOf(ru.typizer.errors.InternalServerException.class, ru.typizer.errors.ApiException.from(500, null, ""));
        assertInstanceOf(ru.typizer.errors.BadRequestException.class,
                ru.typizer.errors.ApiException.from(400, null, "{\"code\":\"brand_new_code\",\"title\":\"x\"}"));
        assertEquals("brand_new_code", ru.typizer.errors.ApiException.from(400, null, "{\"code\":\"brand_new_code\",\"title\":\"x\"}").code());
    }
}
