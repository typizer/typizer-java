package ru.typizer.models;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Planned maintenance or an incident. */
public final class MaintenanceEvent extends TypizerObject {
    static final Set<String> FIELDS = fields("object", "id", "kind", "title", "message_ru", "components", "starts_at", "ends_at", "status");

    public MaintenanceEvent(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** {@code id}. */
    public String id() {
        return str("id");
    }

    /** {@code kind}. */
    public String kind() {
        return str("kind");
    }

    /** {@code title}. */
    public String title() {
        return str("title");
    }

    /** {@code message_ru}. */
    public String messageRu() {
        return str("message_ru");
    }

    /** {@code components}. */
    public List<String> components() {
        return strings("components");
    }

    /** {@code starts_at}. */
    public OffsetDateTime startsAt() {
        return dateTime("starts_at");
    }

    /** {@code ends_at}. */
    public OffsetDateTime endsAt() {
        return dateTime("ends_at");
    }

    /** {@code status}. */
    public String status() {
        return str("status");
    }
}
