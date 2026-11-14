package ru.typizer;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** Period and grouping of {@code client.usage(…)}. */
public final class UsageParams {
    final Map<String, Object> query;

    private UsageParams(Map<String, Object> query) {
        this.query = query;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, Object> q = new LinkedHashMap<>();

        private Builder() {}

        public Builder from(LocalDate from) {
            q.put("from", from);
            return this;
        }

        public Builder to(LocalDate to) {
            q.put("to", to);
            return this;
        }

        /** {@code day} (default), {@code typizer} or {@code key}. */
        public Builder groupBy(String groupBy) {
            q.put("group_by", groupBy);
            return this;
        }

        public UsageParams build() {
            return new UsageParams(new LinkedHashMap<>(q));
        }
    }
}
