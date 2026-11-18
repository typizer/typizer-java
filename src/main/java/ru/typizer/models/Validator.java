package ru.typizer.models;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

/**
 * Your own field check ({@code val_…}), referenced from a schema as {@code x-typizer-check:
 * "my/<slug>@<major>"}. {@link #version()} is the current version; {@code validators().get(…)}
 * returns a {@link ValidatorDetail} with every version and the typizers that use it.
 */
public class Validator extends TypizerObject {
    static final Set<String> FIELDS = fields("object", "id", "ref", "slug", "name", "description", "version",
            "versions_count", "catalog", "forked_from", "created_at", "updated_at");

    public Validator(Map<String, Object> data) {
        super(data, FIELDS);
    }

    Validator(Map<String, Object> data, Set<String> knownFields) {
        super(data, knownFields);
    }

    /** {@code val_…}. */
    public final String id() {
        return str("id");
    }

    /** The reference for {@code x-typizer-check}, without a version: {@code my/contract-no}. */
    public final String ref() {
        return str("ref");
    }

    /** {@code contract-no}. */
    public final String slug() {
        return str("slug");
    }

    /** {@code name}. */
    public final String name() {
        return str("name");
    }

    /** {@code description}. */
    public final String description() {
        return str("description");
    }

    /** The current version: spec, tests, explanation. */
    public final ValidatorVersion version() {
        return object("version", ValidatorVersion::new);
    }

    /** {@code versions_count}. */
    public final Integer versionsCount() {
        return integer("versions_count");
    }

    /** Publication in the catalog ({@code ref}, {@code visibility}), or {@code null}. */
    public final Map<String, Object> catalog() {
        return map("catalog");
    }

    /** Where it was copied from ({@code ref}, {@code version}), or {@code null}. */
    public final Map<String, Object> forkedFrom() {
        return map("forked_from");
    }

    /** {@code created_at}. */
    public final OffsetDateTime createdAt() {
        return dateTime("created_at");
    }

    /** {@code updated_at}. */
    public final OffsetDateTime updatedAt() {
        return dateTime("updated_at");
    }

    @Override
    public String toString() {
        // Not the tests: their values may come from documents.
        ValidatorVersion v = version();
        return getClass().getSimpleName() + "{id=" + id() + ", ref=" + ref() + ", name=" + name() + ", version="
                + (v != null ? v.version() : null) + "}";
    }
}
