package ru.typizer;

/**
 * Paging of a plain list (typizers, typizer versions, batches, webhook endpoints, deliveries):
 * {@code limit} 1–100 (20 by default) and where to start. {@link Page#autoPager()} pages by itself.
 */
public final class ListParams {
    final Integer limit;
    final String startingAfter;

    private ListParams(Integer limit, String startingAfter) {
        this.limit = limit;
        this.startingAfter = startingAfter;
    }

    /** Defaults: 20 per page, from the newest. */
    public static ListParams none() {
        return new ListParams(null, null);
    }

    /** Items per page, 1–100. */
    public static ListParams limit(int limit) {
        return new ListParams(checkLimit(limit), null);
    }

    /** Items per page, and the id after which the page starts (a {@code next_cursor}). */
    public static ListParams of(Integer limit, String startingAfter) {
        return new ListParams(limit == null ? null : checkLimit(limit), startingAfter);
    }

    static int checkLimit(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be 1–100");
        return limit;
    }
}
