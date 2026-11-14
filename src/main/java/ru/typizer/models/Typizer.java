package ru.typizer.models;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

/** A typizer: a JSON Schema saved once and run by its id ({@code tz_…}). */
public final class Typizer extends TypizerObject {
    static final Set<String> FIELDS = fields("object", "id", "name", "description", "version", "schema", "instructions", "default_format", "mode", "catalog_source", "created_at", "updated_at");

    public Typizer(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** {@code id}. */
    public String id() {
        return str("id");
    }

    /** {@code name}. */
    public String name() {
        return str("name");
    }

    /** {@code description}. */
    public String description() {
        return str("description");
    }

    /** {@code version}. */
    public Integer version() {
        return integer("version");
    }

    /** {@code schema}. */
    public Map<String, Object> schema() {
        return map("schema");
    }

    /** {@code instructions}. */
    public String instructions() {
        return str("instructions");
    }

    /** {@code default_format}. */
    public Format defaultFormat() {
        return enumValue("default_format", Format.class);
    }

    /** The raw value of {@code default_format} (also for values newer than this SDK). */
    public String defaultFormatValue() {
        return str("default_format");
    }

    /** {@code mode}. */
    public Mode mode() {
        return enumValue("mode", Mode.class);
    }

    /** The raw value of {@code mode} (also for values newer than this SDK). */
    public String modeValue() {
        return str("mode");
    }

    /** {@code catalog_source}. */
    public Map<String, Object> catalogSource() {
        return map("catalog_source");
    }

    /** {@code created_at}. */
    public OffsetDateTime createdAt() {
        return dateTime("created_at");
    }

    /** {@code updated_at}. */
    public OffsetDateTime updatedAt() {
        return dateTime("updated_at");
    }
}
