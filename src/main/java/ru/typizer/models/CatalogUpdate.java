package ru.typizer.models;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** A newer catalog version for a typizer installed or forked from the catalog. */
public final class CatalogUpdate extends TypizerObject {
    static final Set<String> FIELDS = fields("object", "typizer_id", "relation", "from", "to", "breaking", "changelog", "changes");

    public CatalogUpdate(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** {@code typizer_id}. */
    public String typizerId() {
        return str("typizer_id");
    }

    /** {@code relation}. */
    public String relation() {
        return str("relation");
    }

    /** {@code from}. */
    public String from() {
        return str("from");
    }

    /** {@code to}. */
    public String to() {
        return str("to");
    }

    /** {@code breaking}. */
    public boolean breaking() {
        return flag("breaking");
    }

    /** {@code changelog}. */
    public List<Object> changelog() {
        return list("changelog");
    }

    /** {@code changes}. */
    public List<SchemaChange> changes() {
        return objects("changes", SchemaChange::new);
    }
}
