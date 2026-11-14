package ru.typizer.models;

import java.util.Map;
import java.util.Set;

/** Whether a catalog entry is in the favorites. */
public final class CatalogFavorite extends TypizerObject {
    static final Set<String> FIELDS = fields("object", "entry_id", "favorited");

    public CatalogFavorite(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** {@code entry_id}. */
    public String entryId() {
        return str("entry_id");
    }

    /** {@code favorited}. */
    public boolean favorited() {
        return flag("favorited");
    }
}
