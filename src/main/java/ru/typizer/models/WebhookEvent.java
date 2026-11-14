package ru.typizer.models;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

/**
 * A verified webhook event ({@code Webhooks.verify(…)}). {@link #id()} equals the
 * {@code webhook-id} header — use it to drop duplicate deliveries.
 */
public final class WebhookEvent extends TypizerObject {
    static final Set<String> FIELDS = fields("object", "id", "type", "created_at", "data");

    public WebhookEvent(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** {@code evt_…}: the deduplication key. */
    public String id() {
        return str("id");
    }

    /** {@code run.succeeded}, {@code run.failed}, {@code batch.completed}, {@code balance.low}, or {@code UNKNOWN}. */
    public WebhookEventType type() {
        return enumValue("type", WebhookEventType.class);
    }

    /** The raw type (also for types newer than this SDK). */
    public String typeValue() {
        return str("type");
    }

    public OffsetDateTime createdAt() {
        return dateTime("created_at");
    }

    /** {@code data} as sent. */
    public Map<String, Object> data() {
        return map("data");
    }

    /** {@code data.object} as sent: a run, a batch or another object. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> dataObject() {
        Map<String, Object> data = data();
        Object o = data == null ? null : data.get("object");
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    /** The run of a {@code run.*} event; {@code null} for other types. */
    public Run run() {
        String t = typeValue();
        Map<String, Object> o = dataObject();
        return t != null && t.startsWith("run.") && o != null ? new Run(o) : null;
    }

    /** The batch of a {@code batch.completed} event; {@code null} for other types. */
    public Batch batch() {
        Map<String, Object> o = dataObject();
        return "batch.completed".equals(typeValue()) && o != null ? new Batch(o) : null;
    }
}
