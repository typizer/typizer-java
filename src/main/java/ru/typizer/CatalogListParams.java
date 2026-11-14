package ru.typizer;

import java.util.LinkedHashMap;
import java.util.Map;

/** Filters of {@code catalog().list(…)}. Only what you set is sent. */
public final class CatalogListParams {
    final Map<String, Object> query;

    private CatalogListParams(Map<String, Object> query) {
        this.query = query;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, Object> q = new LinkedHashMap<>();

        private Builder() {}

        /** Search text. */
        public Builder q(String text) {
            q.put("q", text);
            return this;
        }

        /** {@code invoices}, {@code bank}, {@code shipping}, {@code contracts}, {@code registries}, … */
        public Builder category(String category) {
            q.put("category", category);
            return this;
        }

        /** {@code official} or {@code community}. */
        public Builder kind(String kind) {
            q.put("kind", kind);
            return this;
        }

        /** Publisher slug. */
        public Builder publisher(String publisher) {
            q.put("publisher", publisher);
            return this;
        }

        /** Only the favorites of the organization. */
        public Builder favorites(boolean favorites) {
            q.put("favorites", favorites);
            return this;
        }

        /** Sort order, as in the API reference. */
        public Builder sort(String sort) {
            q.put("sort", sort);
            return this;
        }

        /** Entries per page, 1–100. */
        public Builder limit(int limit) {
            q.put("limit", ListParams.checkLimit(limit));
            return this;
        }

        /** Start from this cursor (a {@code next_cursor}). */
        public Builder cursor(String cursor) {
            q.put("cursor", cursor);
            return this;
        }

        public CatalogListParams build() {
            return new CatalogListParams(new LinkedHashMap<>(q));
        }
    }
}
