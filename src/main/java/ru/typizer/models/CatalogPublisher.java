package ru.typizer.models;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A catalog publisher with its entries. */
public final class CatalogPublisher extends TypizerObject {
    static final Set<String> FIELDS = fields("object", "id", "slug", "display_name", "badges", "about", "website", "since", "stats", "entries");

    public CatalogPublisher(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** {@code id}. */
    public String id() {
        return str("id");
    }

    /** {@code slug}. */
    public String slug() {
        return str("slug");
    }

    /** {@code display_name}. */
    public String displayName() {
        return str("display_name");
    }

    /** {@code badges}. */
    public List<String> badges() {
        return strings("badges");
    }

    /** {@code about}. */
    public String about() {
        return str("about");
    }

    /** {@code website}. */
    public String website() {
        return str("website");
    }

    /** {@code since}. */
    public OffsetDateTime since() {
        return dateTime("since");
    }

    /** {@code stats}. */
    public Map<String, Object> stats() {
        return map("stats");
    }

    /** {@code entries}. */
    public List<CatalogEntryBrief> entries() {
        return objects("entries", CatalogEntryBrief::new);
    }
}
