package ru.typizer.models;

import java.util.Map;
import java.util.Set;

/** The answer to a delete: the id and {@code deleted: true}. */
public final class Deleted extends TypizerObject {
    static final Set<String> FIELDS = fields("object", "id", "deleted");

    public Deleted(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** {@code id}. */
    public String id() {
        return str("id");
    }

    /** {@code deleted}. */
    public boolean deleted() {
        return flag("deleted");
    }
}
