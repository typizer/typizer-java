package ru.typizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import ru.typizer.errors.WebhookVerificationException;
import ru.typizer.internal.Json;
import ru.typizer.models.Run;
import ru.typizer.models.RunStatus;
import ru.typizer.models.WebhookEvent;
import ru.typizer.models.WebhookEventType;

/** SC-32: every case of packages/sdk-spec/conformance/fixtures/webhook-vectors.json. */
class WebhookTest {
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> vectors() throws IOException {
        Path file = MockServer.find("packages/sdk-spec/conformance/fixtures/webhook-vectors.json",
                "../sdk-spec/conformance/fixtures/webhook-vectors.json", "src/test/resources/webhook-vectors.json");
        assertNotNull(file, "webhook-vectors.json not found");
        Map<String, Object> doc = Json.parseObject(new String(java.nio.file.Files.readAllBytes(file), StandardCharsets.UTF_8));
        assertEquals(300L, doc.get("tolerance_sec"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object c : (List<Object>) doc.get("cases")) out.add((Map<String, Object>) c);
        return out;
    }

    @TestFactory
    @DisplayName("SC-32 webhook vectors")
    Stream<DynamicTest> sc32_webhookVectors() throws IOException {
        List<Map<String, Object>> cases = vectors();
        assertEquals(19, cases.size());
        return cases.stream().map(c -> DynamicTest.dynamicTest("SC-32 " + c.get("name"), () -> check(c)));
    }

    @SuppressWarnings("unchecked")
    private static void check(Map<String, Object> c) {
        String secret = (String) c.get("secret");
        Map<String, Object> headers = (Map<String, Object>) c.get("headers");
        String body = (String) c.get("body");
        Instant now = Instant.ofEpochSecond(((Number) c.get("now")).longValue());
        boolean valid = Boolean.TRUE.equals(c.get("valid"));

        assertEquals(valid, Webhooks.isValid(body.getBytes(StandardCharsets.UTF_8), Webhooks.lookup(headers), secret,
                Webhooks.DEFAULT_TOLERANCE, now), "isValid");
        if (valid) {
            WebhookEvent event = Webhooks.verify(body, headers, secret, Duration.ofSeconds(300), now);
            assertEquals(c.get("event_type"), event.typeValue());
            assertEquals(headers.entrySet().stream().filter(h -> h.getKey().equalsIgnoreCase("webhook-id")).findFirst().get().getValue(),
                    event.id());
            if (event.typeValue().equals("run.succeeded") || event.typeValue().equals("run.failed")) {
                Run run = event.run();
                assertNotNull(run);
                assertTrue(run.isTerminal());
                assertEquals(event.typeValue().equals("run.failed") ? RunStatus.FAILED : RunStatus.SUCCEEDED, run.status());
            } else {
                assertEquals(WebhookEventType.UNKNOWN, event.type());
                assertNull(event.batch());
                assertNotNull(event.dataObject());
            }
        } else {
            WebhookVerificationException e = assertThrows(WebhookVerificationException.class,
                    () -> Webhooks.verify(body, headers, secret, Duration.ofSeconds(300), now));
            assertEquals(c.get("reason"), e.reason());
            assertFalse(e.getMessage().contains(secret));
        }
    }

    @Test
    @DisplayName("SC-32 full run event carries fields and headers by function")
    void sc32_fullRunEventCarriesFieldsAndHeadersByFunction() throws IOException {
        Map<String, Object> c = null;
        for (Map<String, Object> v : vectors()) if ("full_run_event".equals(v.get("name"))) c = v;
        assertNotNull(c);
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) (Map<?, ?>) c.get("headers");
        WebhookEvent event = Webhooks.verify(((String) c.get("body")).getBytes(StandardCharsets.UTF_8), headers::get,
                (String) c.get("secret"), Duration.ofSeconds(300), Instant.ofEpochSecond(((Number) c.get("now")).longValue()));
        assertEquals(WebhookEventType.RUN_SUCCEEDED, event.type());
        Run run = event.run();
        assertTrue(run.needsReview());
        assertEquals("invoice_number", run.fieldsToReview().get(0).path());
    }

    @Test
    @DisplayName("SC-32 invalid secret and live sample")
    void sc32_invalidSecretAndLiveSample() {
        Map<String, String> h = Collections.singletonMap("webhook-id", "evt_1");
        WebhookVerificationException missing = assertThrows(WebhookVerificationException.class,
                () -> Webhooks.verify("{}", h, "whsec_AAAA"));
        assertEquals("missing_header", missing.reason());

        // A sample the mock signs now, with the real clock — and the secret too short to be one.
        Map<String, Object> sample = Json.parseObject(MockServer.get("/_mock/webhooks/sample?type=run.succeeded"));
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) sample.get("headers");
        String body = (String) sample.get("body");
        assertEquals(WebhookEventType.RUN_SUCCEEDED, Webhooks.verify(body, headers, (String) sample.get("secret")).type());
        WebhookVerificationException shortSecret = assertThrows(WebhookVerificationException.class,
                () -> Webhooks.verify(body, headers, "whsec_c2hvcnQ="));
        assertEquals("invalid_secret", shortSecret.reason());
    }
}
