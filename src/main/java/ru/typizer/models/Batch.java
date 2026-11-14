package ru.typizer.models;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

/** A batch: one typizer over many files. */
public final class Batch extends TypizerObject {
    static final Set<String> FIELDS = fields("object", "id", "typizer_id", "status", "total", "succeeded", "failed", "created_at", "completed_at");

    public Batch(Map<String, Object> data) {
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

    /** {@code status}. */
    public BatchStatus status() {
        return enumValue("status", BatchStatus.class);
    }

    /** The raw value of {@code status} (also for values newer than this SDK). */
    public String statusValue() {
        return str("status");
    }

    /** {@code total}. */
    public Integer total() {
        return integer("total");
    }

    /** {@code succeeded}. */
    public Integer succeeded() {
        return integer("succeeded");
    }

    /** {@code failed}. */
    public Integer failed() {
        return integer("failed");
    }

    /** {@code created_at}. */
    public OffsetDateTime createdAt() {
        return dateTime("created_at");
    }

    /** {@code completed_at}. */
    public OffsetDateTime completedAt() {
        return dateTime("completed_at");
    }
}
