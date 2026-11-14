package ru.typizer.models;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The check result of one leaf field of a run ({@code seller.inn}, {@code items[0].amount}). The API
 * does not return a numeric confidence — instead each field says whether its checks passed.
 */
public final class FieldResult extends TypizerObject {
    static final Set<String> FIELDS = fields("status", "reason", "sources");

    private final String path;

    public FieldResult(String path, Map<String, Object> data) {
        super(data, FIELDS);
        this.path = path;
    }

    /** The path of the field in the API notation: {@code seller.inn}, {@code items[0].amount}. */
    public String path() {
        return path;
    }

    /** {@code ok} — checks passed; {@code warn} — a value, but a check failed; {@code missing} — no value. */
    public FieldStatus status() {
        return enumValue("status", FieldStatus.class);
    }

    /** The raw status (also for values newer than this SDK). */
    public String statusValue() {
        return str("status");
    }

    /**
     * Why the field is {@code warn}: {@code checksum_mismatch}, {@code invalid_format},
     * {@code sum_mismatch}, {@code implausible_date}, {@code no_source}, …; otherwise {@code null}.
     */
    public String reason() {
        return str("reason");
    }

    /** Where in the document the value was found. */
    public List<FieldSource> sources() {
        return objects("sources", FieldSource::new);
    }

    @Override
    public String toString() {
        // Never the source text: it is document data.
        return "FieldResult{path=" + path + ", status=" + statusValue() + (reason() != null ? ", reason=" + reason() : "")
                + "}";
    }
}
