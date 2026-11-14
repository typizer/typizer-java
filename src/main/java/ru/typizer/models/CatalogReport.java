package ru.typizer.models;

import java.util.Map;
import java.util.Set;

/** A report about a catalog entry. */
public final class CatalogReport extends TypizerObject {
    static final Set<String> FIELDS = fields("object", "id", "status");

    public CatalogReport(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** {@code id}. */
    public String id() {
        return str("id");
    }

    /** {@code status}. */
    public String status() {
        return str("status");
    }
}
