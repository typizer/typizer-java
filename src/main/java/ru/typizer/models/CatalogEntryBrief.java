package ru.typizer.models;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A catalog entry as listed by {@code catalog().list()}. */
public class CatalogEntryBrief extends TypizerObject {
    static final Set<String> FIELDS = fields("object", "id", "type", "ref", "kind", "slug", "title", "summary", "category", "tags", "publisher", "featured", "latest_version", "fields_preview", "rule_preview", "validator", "stats", "web_url", "updated_at", "viewer");

    public CatalogEntryBrief(Map<String, Object> data) {
        super(data, FIELDS);
    }

    protected CatalogEntryBrief(Map<String, Object> data, Set<String> known) {
        super(data, known);
    }

    /** {@code id}. */
    public String id() {
        return str("id");
    }

    /** {@code ref}. */
    public String ref() {
        return str("ref");
    }

    /** {@code kind}. */
    public String kind() {
        return str("kind");
    }

    /** {@code slug}. */
    public String slug() {
        return str("slug");
    }

    /** {@code title}. */
    public String title() {
        return str("title");
    }

    /** {@code summary}. */
    public String summary() {
        return str("summary");
    }

    /** {@code category}. */
    public String category() {
        return str("category");
    }

    /** {@code tags}. */
    public List<String> tags() {
        return strings("tags");
    }

    /** {@code publisher}. */
    public CatalogPublisherBrief publisher() {
        return object("publisher", CatalogPublisherBrief::new);
    }

    /** {@code featured}. */
    public boolean featured() {
        return flag("featured");
    }

    /** {@code latest_version}. */
    public CatalogVersionBrief latestVersion() {
        return object("latest_version", CatalogVersionBrief::new);
    }

    /** {@code fields_preview}. */
    public List<String> fieldsPreview() {
        return strings("fields_preview");
    }

    /** {@code type} of a schema entry. */
    public static final String TYPE_SCHEMA = "schema";
    /** {@code type} of a field validator entry. */
    public static final String TYPE_VALIDATOR = "validator";

    /**
     * {@code schema} — a document schema (added to your typizers) or {@code validator} — a field
     * check (a field refers to it in {@code x-typizer-check}, nothing to install). A newer value is
     * kept as is.
     */
    public String type() {
        return str("type");
    }

    /** A validator's rule as short chips («2 буквы», «–», «4 цифры»); {@code null} for a schema. */
    public List<String> rulePreview() {
        return strings("rule_preview");
    }

    /** What a field refers to and the rule; {@code null} for a schema. */
    public CatalogValidatorBrief validator() {
        return object("validator", CatalogValidatorBrief::new);
    }

    /** {@code stats}. */
    public Map<String, Object> stats() {
        return map("stats");
    }

    /** {@code web_url}. */
    public String webUrl() {
        return str("web_url");
    }

    /** {@code updated_at}. */
    public OffsetDateTime updatedAt() {
        return dateTime("updated_at");
    }

    /** {@code viewer}. */
    public Map<String, Object> viewer() {
        return map("viewer");
    }
}
