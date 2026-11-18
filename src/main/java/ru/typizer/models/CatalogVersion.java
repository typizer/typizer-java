package ru.typizer.models;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** A version of a catalog entry with its schema. */
public final class CatalogVersion extends CatalogVersionBrief {
    static final Set<String> FIELDS = fields("object", "id", "version", "major", "breaking", "changelog", "field_count", "published_at", "schema", "instructions", "example_output", "changes", "validator");

    public CatalogVersion(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** {@code schema}. */
    public Map<String, Object> schema() {
        return map("schema");
    }

    /** {@code instructions}. */
    public String instructions() {
        return str("instructions");
    }

    /** {@code example_output}. */
    public Map<String, Object> exampleOutput() {
        return map("example_output");
    }

    /** {@code changes}. */
    public List<SchemaChange> changes() {
        return objects("changes", SchemaChange::new);
    }

    /** A validator version's rule, published tests and explanation; {@code null} for a schema. */
    public CatalogValidatorVersion validator() {
        return object("validator", CatalogValidatorVersion::new);
    }
}
