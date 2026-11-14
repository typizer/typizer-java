package ru.typizer.models;

import java.util.Map;
import java.util.Set;

/** One row of {@link Usage}. */
public final class UsageRow extends TypizerObject {
    static final Set<String> FIELDS = fields("object", "key", "runs", "succeeded", "failed", "pages_billed");

    public UsageRow(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** {@code key}. */
    public String key() {
        return str("key");
    }

    /** {@code runs}. */
    public Long runs() {
        return longValue("runs");
    }

    /** {@code succeeded}. */
    public Long succeeded() {
        return longValue("succeeded");
    }

    /** {@code failed}. */
    public Long failed() {
        return longValue("failed");
    }

    /** {@code pages_billed}. */
    public Long pagesBilled() {
        return longValue("pages_billed");
    }
}
