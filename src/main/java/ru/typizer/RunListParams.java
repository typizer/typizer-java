package ru.typizer;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import ru.typizer.models.RunStatus;

/** Filters of {@code runs().list(…)}. Only what you set is sent. */
public final class RunListParams {
    final Map<String, Object> query;

    private RunListParams(Map<String, Object> query) {
        this.query = query;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, Object> q = new LinkedHashMap<>();

        private Builder() {}

        public Builder status(RunStatus status) {
            q.put("status", ExtractParams.enumValue(status, "status"));
            return this;
        }

        public Builder status(String status) {
            q.put("status", status);
            return this;
        }

        public Builder typizerId(String typizerId) {
            q.put("typizer_id", typizerId);
            return this;
        }

        public Builder batchId(String batchId) {
            q.put("batch_id", batchId);
            return this;
        }

        /** {@code api}, {@code my}, {@code mcp}, … */
        public Builder source(String source) {
            q.put("source", source);
            return this;
        }

        public Builder createdAfter(OffsetDateTime createdAfter) {
            q.put("created_after", createdAfter);
            return this;
        }

        public Builder createdBefore(OffsetDateTime createdBefore) {
            q.put("created_before", createdBefore);
            return this;
        }

        /** Runs per page, 1–100 (20 by default). */
        public Builder limit(int limit) {
            q.put("limit", ListParams.checkLimit(limit));
            return this;
        }

        /** Start after this run id (a {@code next_cursor}). */
        public Builder startingAfter(String runId) {
            q.put("starting_after", runId);
            return this;
        }

        public RunListParams build() {
            return new RunListParams(new LinkedHashMap<>(q));
        }
    }
}
