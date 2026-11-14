package ru.typizer.models;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

/** One lot of pages on the balance (free, bonus, purchase…). */
public final class BalanceLot extends TypizerObject {
    static final Set<String> FIELDS = fields("object", "source", "pages_left", "expires_at");

    public BalanceLot(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** {@code source}. */
    public String source() {
        return str("source");
    }

    /** {@code pages_left}. */
    public Long pagesLeft() {
        return longValue("pages_left");
    }

    /** {@code expires_at}. */
    public OffsetDateTime expiresAt() {
        return dateTime("expires_at");
    }
}
