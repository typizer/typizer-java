package ru.typizer;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Settings of one call, on top of the client's: timeout of an HTTP attempt, number of retries,
 * {@code Idempotency-Key} and extra headers. Every resource method has an overload that takes them.
 *
 * <pre>{@code
 * client.runs().create(params, RequestOptions.builder().idempotencyKey("invoice-" + invoiceId).build());
 * }</pre>
 */
public final class RequestOptions {
    /** No overrides. */
    public static final RequestOptions NONE = builder().build();

    private final Duration timeout;
    private final Integer maxRetries;
    private final String idempotencyKey;
    private final Map<String, String> extraHeaders;

    private RequestOptions(Builder b) {
        this.timeout = b.timeout;
        this.maxRetries = b.maxRetries;
        this.idempotencyKey = b.idempotencyKey;
        this.extraHeaders = Collections.unmodifiableMap(new LinkedHashMap<>(b.extraHeaders));
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Timeout of one HTTP attempt, or null for the client's. */
    public Duration timeout() {
        return timeout;
    }

    /** Retries, or null for the client's. */
    public Integer maxRetries() {
        return maxRetries;
    }

    /** The {@code Idempotency-Key} for a {@code POST}, or null to let the SDK generate one. */
    public String idempotencyKey() {
        return idempotencyKey;
    }

    public Map<String, String> extraHeaders() {
        return extraHeaders;
    }

    public static final class Builder {
        private Duration timeout;
        private Integer maxRetries;
        private String idempotencyKey;
        private final Map<String, String> extraHeaders = new LinkedHashMap<>();

        private Builder() {}

        /** Timeout of one HTTP attempt (connect, send, response). */
        public Builder timeout(Duration timeout) {
            if (timeout != null && (timeout.isNegative() || timeout.isZero()))
                throw new IllegalArgumentException("timeout must be positive");
            this.timeout = timeout;
            return this;
        }

        /** Automatic retries for this call (0 — none). */
        public Builder maxRetries(int maxRetries) {
            if (maxRetries < 0) throw new IllegalArgumentException("maxRetries must be >= 0");
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Your own {@code Idempotency-Key} (1–255 printable ASCII characters): the same key with the
         * same request within 24 hours returns the first result instead of doing the work again —
         * e.g. {@code "invoice-" + invoiceId} for a document taken from a queue that may redeliver it.
         * Without it the SDK generates a fresh UUID per call (and keeps it across its own retries).
         */
        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = validIdempotencyKey(idempotencyKey);
            return this;
        }

        /** An extra header. {@code Authorization} cannot be overridden. */
        public Builder header(String name, String value) {
            extraHeaders.put(name, value);
            return this;
        }

        public RequestOptions build() {
            return new RequestOptions(this);
        }
    }

    static String validIdempotencyKey(String key) {
        if (key == null) return null;
        if (key.isEmpty() || key.length() > 255)
            throw new IllegalArgumentException("idempotencyKey must be 1–255 characters");
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c < 0x20 || c > 0x7e)
                throw new IllegalArgumentException("idempotencyKey must be printable ASCII (0x20–0x7E)");
        }
        return key;
    }

    @Override
    public String toString() {
        return "RequestOptions{timeout=" + timeout + ", maxRetries=" + maxRetries + ", idempotencyKey="
                + (idempotencyKey == null ? null : "set") + ", extraHeaders=" + extraHeaders.keySet() + "}";
    }
}
