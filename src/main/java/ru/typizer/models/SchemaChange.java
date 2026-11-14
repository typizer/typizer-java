package ru.typizer.models;

import java.util.Map;
import java.util.Set;

/** One change of a schema between two versions. */
public final class SchemaChange extends TypizerObject {
    static final Set<String> FIELDS = fields("object", "path", "kind", "breaking");

    public SchemaChange(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** {@code path}. */
    public String path() {
        return str("path");
    }

    /** {@code kind}. */
    public String kind() {
        return str("kind");
    }

    /** {@code breaking}. */
    public boolean breaking() {
        return flag("breaking");
    }
}
