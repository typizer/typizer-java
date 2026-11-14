package ru.typizer.models;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A webhook endpoint. {@code secret} is only returned when the endpoint is created (and is null in an idempotent replay of that call). */
public final class WebhookEndpoint extends TypizerObject {
    static final Set<String> FIELDS = fields("object", "id", "url", "events", "status", "secret", "failing_since", "created_at");

    public WebhookEndpoint(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** {@code id}. */
    public String id() {
        return str("id");
    }

    /** {@code url}. */
    public String url() {
        return str("url");
    }

    /** {@code events}. */
    public List<String> events() {
        return strings("events");
    }

    /** {@code status}. */
    public String status() {
        return str("status");
    }

    /** {@code secret}. */
    public String secret() {
        return str("secret");
    }

    /** {@code failing_since}. */
    public OffsetDateTime failingSince() {
        return dateTime("failing_since");
    }

    /** {@code created_at}. */
    public OffsetDateTime createdAt() {
        return dateTime("created_at");
    }
}
