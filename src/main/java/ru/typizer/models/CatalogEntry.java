package ru.typizer.models;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A catalog entry with its current version and schema. */
public final class CatalogEntry extends CatalogEntryBrief {
    static final Set<String> FIELDS = fields("object", "id", "ref", "kind", "slug", "title", "summary", "category", "tags", "publisher", "featured", "latest_version", "fields_preview", "stats", "web_url", "updated_at", "viewer", "description", "prompt", "visibility", "version", "majors", "lineage", "owner", "created_at");

    public CatalogEntry(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** {@code description}. */
    public String description() {
        return str("description");
    }

    /** {@code prompt}. */
    public String prompt() {
        return str("prompt");
    }

    /** {@code visibility}. */
    public String visibility() {
        return str("visibility");
    }

    /** {@code version}. */
    public CatalogVersion version() {
        return object("version", CatalogVersion::new);
    }

    /** {@code majors}. */
    public List<Object> majors() {
        return list("majors");
    }

    /** {@code lineage}. */
    public Map<String, Object> lineage() {
        return map("lineage");
    }

    /** {@code owner}. */
    public Map<String, Object> owner() {
        return map("owner");
    }

    /** {@code created_at}. */
    public OffsetDateTime createdAt() {
        return dateTime("created_at");
    }
}
