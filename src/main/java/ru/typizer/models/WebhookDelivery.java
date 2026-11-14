package ru.typizer.models;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

/** One delivery attempt of a webhook event. */
public final class WebhookDelivery extends TypizerObject {
    static final Set<String> FIELDS = fields("object", "id", "event_id", "event_type", "status", "attempt", "response_code", "next_attempt_at", "created_at");

    public WebhookDelivery(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** {@code id}. */
    public String id() {
        return str("id");
    }

    /** {@code event_id}. */
    public String eventId() {
        return str("event_id");
    }

    /** {@code event_type}. */
    public String eventType() {
        return str("event_type");
    }

    /** {@code status}. */
    public String status() {
        return str("status");
    }

    /** {@code attempt}. */
    public Integer attempt() {
        return integer("attempt");
    }

    /** {@code response_code}. */
    public Integer responseCode() {
        return integer("response_code");
    }

    /** {@code next_attempt_at}. */
    public OffsetDateTime nextAttemptAt() {
        return dateTime("next_attempt_at");
    }

    /** {@code created_at}. */
    public OffsetDateTime createdAt() {
        return dateTime("created_at");
    }
}
