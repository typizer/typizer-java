package ru.typizer.models;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

/** A version of a catalog entry, without the schema. */
public class CatalogVersionBrief extends TypizerObject {
    static final Set<String> FIELDS = fields("object", "id", "version", "major", "breaking", "changelog", "field_count", "published_at");

    public CatalogVersionBrief(Map<String, Object> data) {
        super(data, FIELDS);
    }

    protected CatalogVersionBrief(Map<String, Object> data, Set<String> known) {
        super(data, known);
    }

    /** {@code id}. */
    public String id() {
        return str("id");
    }

    /** {@code version}. */
    public String version() {
        return str("version");
    }

    /** {@code major}. */
    public Integer major() {
        return integer("major");
    }

    /** {@code breaking}. */
    public boolean breaking() {
        return flag("breaking");
    }

    /** {@code changelog}. */
    public String changelog() {
        return str("changelog");
    }

    /** {@code field_count}. */
    public Integer fieldCount() {
        return integer("field_count");
    }

    /** {@code published_at}. */
    public OffsetDateTime publishedAt() {
        return dateTime("published_at");
    }
}
