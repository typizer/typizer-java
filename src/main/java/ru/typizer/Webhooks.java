package ru.typizer;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import ru.typizer.errors.WebhookVerificationException;
import ru.typizer.internal.Json;
import ru.typizer.models.WebhookEvent;

/**
 * Verifies webhook deliveries ({@code Standard Webhooks}: headers {@code webhook-id},
 * {@code webhook-timestamp}, {@code webhook-signature}; HMAC-SHA256). Needs no client and no API key —
 * only the endpoint's secret {@code whsec_…}.
 *
 * <p>Pass the body exactly as it came: the raw bytes or string, not JSON parsed and written again
 * (in Spring: {@code @RequestBody String body} or {@code byte[]}).
 *
 * <pre>{@code
 * @PostMapping("/typizer/webhook")
 * ResponseEntity<Void> webhook(@RequestBody String body, @RequestHeader HttpHeaders headers) {
 *     WebhookEvent event = Webhooks.verify(body, headers, secret); // WebhookVerificationException → 400
 *     ...
 * }
 * }</pre>
 */
public final class Webhooks {
    /** Allowed clock difference, both ways: 5 minutes. */
    public static final Duration DEFAULT_TOLERANCE = Duration.ofSeconds(300);
    private static final Pattern TIMESTAMP = Pattern.compile("^\\d{1,15}$");

    private Webhooks() {}

    /** Verifies and parses a delivery; headers as a map (names in any case; a list value — its first item). */
    public static WebhookEvent verify(String payload, Map<String, ?> headers, String secret) {
        return verify(bytes(payload), lookup(headers), secret, DEFAULT_TOLERANCE, null);
    }

    public static WebhookEvent verify(byte[] payload, Map<String, ?> headers, String secret) {
        return verify(payload, lookup(headers), secret, DEFAULT_TOLERANCE, null);
    }

    /** Headers through a lookup function, e.g. {@code request::getHeader} of a servlet request. */
    public static WebhookEvent verify(String payload, Function<String, String> headers, String secret) {
        return verify(bytes(payload), headers, secret, DEFAULT_TOLERANCE, null);
    }

    public static WebhookEvent verify(byte[] payload, Function<String, String> headers, String secret) {
        return verify(payload, headers, secret, DEFAULT_TOLERANCE, null);
    }

    /** With your own tolerance and clock ({@code now} null — the current time). */
    public static WebhookEvent verify(String payload, Map<String, ?> headers, String secret, Duration tolerance,
            Instant now) {
        return verify(bytes(payload), lookup(headers), secret, tolerance, now);
    }

    /**
     * The full form.
     *
     * @throws WebhookVerificationException with {@link WebhookVerificationException#reason()} —
     *     {@code missing_header}, {@code invalid_timestamp}, {@code timestamp_out_of_tolerance},
     *     {@code invalid_secret} or {@code signature_mismatch}
     */
    public static WebhookEvent verify(byte[] payload, Function<String, String> headers, String secret,
            Duration tolerance, Instant now) {
        if (payload == null) throw new IllegalArgumentException("payload must not be null");
        if (headers == null) throw new IllegalArgumentException("headers must not be null");
        String id = headers.apply("webhook-id");
        String timestamp = headers.apply("webhook-timestamp");
        String signature = headers.apply("webhook-signature");
        if (empty(id) || empty(timestamp) || empty(signature)) {
            throw new WebhookVerificationException(WebhookVerificationException.MISSING_HEADER,
                    "webhook-id, webhook-timestamp and webhook-signature headers are required");
        }
        timestamp = timestamp.trim();
        if (!TIMESTAMP.matcher(timestamp).matches()) {
            throw new WebhookVerificationException(WebhookVerificationException.INVALID_TIMESTAMP,
                    "webhook-timestamp must be Unix seconds");
        }
        long ts = Long.parseLong(timestamp);
        long nowSec = (now != null ? now : Instant.now()).getEpochSecond();
        long tol = (tolerance != null ? tolerance : DEFAULT_TOLERANCE).getSeconds();
        if (Math.abs(nowSec - ts) > tol) {
            throw new WebhookVerificationException(WebhookVerificationException.TIMESTAMP_OUT_OF_TOLERANCE,
                    "webhook-timestamp is more than " + tol + " s away from now");
        }
        byte[] key = secretKey(secret);
        byte[] expected = hmac(key, id, timestamp, payload);

        boolean matched = false;
        for (String entry : signature.trim().split(" +")) {
            int comma = entry.indexOf(',');
            if (comma < 0 || !"v1".equals(entry.substring(0, comma))) continue; // other versions: skipped
            byte[] candidate;
            try {
                candidate = Base64.getDecoder().decode(entry.substring(comma + 1));
            } catch (IllegalArgumentException notBase64) {
                continue;
            }
            // Constant time, and every entry is compared: the order does not reveal which one matched.
            matched |= MessageDigest.isEqual(expected, candidate);
        }
        if (!matched) {
            throw new WebhookVerificationException(WebhookVerificationException.SIGNATURE_MISMATCH,
                    "No v1 signature matches the body (was it re-serialized, or is the secret of another endpoint?)");
        }
        Object parsed;
        try {
            parsed = Json.parse(new String(payload, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The webhook payload is not JSON", e);
        }
        if (!(parsed instanceof Map)) throw new IllegalArgumentException("The webhook payload is not a JSON object");
        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) parsed;
        return new WebhookEvent(event);
    }

    /** Like {@link #verify(String, Map, String)}, but {@code true}/{@code false} instead of an exception. */
    public static boolean isValid(String payload, Map<String, ?> headers, String secret) {
        return isValid(bytes(payload), lookup(headers), secret, DEFAULT_TOLERANCE, null);
    }

    public static boolean isValid(byte[] payload, Map<String, ?> headers, String secret) {
        return isValid(payload, lookup(headers), secret, DEFAULT_TOLERANCE, null);
    }

    public static boolean isValid(String payload, Function<String, String> headers, String secret) {
        return isValid(bytes(payload), headers, secret, DEFAULT_TOLERANCE, null);
    }

    public static boolean isValid(byte[] payload, Function<String, String> headers, String secret, Duration tolerance,
            Instant now) {
        try {
            verify(payload, headers, secret, tolerance, now);
            return true;
        } catch (WebhookVerificationException | IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] secretKey(String secret) {
        if (secret == null) throw new WebhookVerificationException(WebhookVerificationException.INVALID_SECRET, "No secret");
        String s = secret.trim();
        if (s.startsWith("whsec_")) s = s.substring("whsec_".length());
        byte[] key;
        try {
            key = Base64.getDecoder().decode(s);
        } catch (IllegalArgumentException e) {
            throw new WebhookVerificationException(WebhookVerificationException.INVALID_SECRET,
                    "The secret is not whsec_ followed by base64");
        }
        if (key.length < 24) {
            throw new WebhookVerificationException(WebhookVerificationException.INVALID_SECRET,
                    "The secret is too short (" + key.length + " bytes, at least 24)");
        }
        return key;
    }

    private static byte[] hmac(byte[] key, String id, String timestamp, byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update((id + "." + timestamp + ".").getBytes(StandardCharsets.UTF_8));
            return mac.doFinal(payload);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    private static boolean empty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static byte[] bytes(String payload) {
        if (payload == null) throw new IllegalArgumentException("payload must not be null");
        return payload.getBytes(StandardCharsets.UTF_8);
    }

    /** Case-insensitive lookup in a map of headers; a collection or array value gives its first item. */
    static Function<String, String> lookup(Map<String, ?> headers) {
        if (headers == null) throw new IllegalArgumentException("headers must not be null");
        return name -> {
            for (Map.Entry<String, ?> e : headers.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) return first(e.getValue());
            }
            return null;
        };
    }

    private static String first(Object v) {
        if (v == null) return null;
        if (v instanceof Collection) {
            Collection<?> c = (Collection<?>) v;
            return c.isEmpty() ? null : String.valueOf(c.iterator().next());
        }
        if (v instanceof String[]) {
            String[] a = (String[]) v;
            return a.length == 0 ? null : a[0];
        }
        return String.valueOf(v);
    }
}
