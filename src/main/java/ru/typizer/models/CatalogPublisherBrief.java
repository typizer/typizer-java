package ru.typizer.models;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** The publisher of a catalog entry. */
public final class CatalogPublisherBrief extends TypizerObject {
    static final Set<String> FIELDS = fields("object", "id", "slug", "display_name", "badges");

    public CatalogPublisherBrief(Map<String, Object> data) {
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
}
