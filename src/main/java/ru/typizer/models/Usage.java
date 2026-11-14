package ru.typizer.models;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Runs and billed pages over a period, grouped by day, typizer or key. */
public final class Usage extends TypizerObject {
    static final Set<String> FIELDS = fields("object", "from", "to", "group_by", "data", "has_more");

    public Usage(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** {@code from}. */
    public LocalDate from() {
        return date("from");
    }

    /** {@code to}. */
    public LocalDate to() {
        return date("to");
    }

    /** {@code group_by}. */
    public String groupBy() {
        return str("group_by");
    }

    /** {@code data}. */
    public List<UsageRow> data() {
        return objects("data", UsageRow::new);
    }

    /** {@code has_more}. */
    public boolean hasMore() {
        return flag("has_more");
    }
}
