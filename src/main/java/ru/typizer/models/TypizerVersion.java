package ru.typizer.models;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One saved version of a typizer's schema. */
public final class TypizerVersion extends TypizerObject {
    static final Set<String> FIELDS = fields("object", "id", "typizer_id", "version", "schema", "instructions", "checks", "created_at");

    public TypizerVersion(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** {@code id}. */
    public String id() {
        return str("id");
    }

    /** {@code typizer_id}. */
    public String typizerId() {
        return str("typizer_id");
    }

    /** {@code version}. */
    public Integer version() {
        return integer("version");
    }

    /** {@code schema}. */
    public Map<String, Object> schema() {
        return map("schema");
    }

    /** {@code instructions}. */
    public String instructions() {
        return str("instructions");
    }

    /** The checks this version is locked on (references with their exact versions). */
    public List<TypizerCheck> checks() {
        return objects("checks", TypizerCheck::new);
    }

    /** {@code created_at}. */
    public OffsetDateTime createdAt() {
        return dateTime("created_at");
    }
}
