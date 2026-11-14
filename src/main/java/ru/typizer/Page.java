package ru.typizer;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * One page of a list ({@code runs().list()}, {@code typizers().list()}, …): newest first, 20 items by
 * default (limit 1–100). {@link #autoPager()} goes through the items of all pages, fetching the next
 * page only when the previous one is used up:
 *
 * <pre>{@code
 * for (Run run : client.runs().list(RunListParams.builder().status(RunStatus.FAILED).build()).autoPager()) {
 *     System.out.println(run.id());
 * }
 * }</pre>
 */
public final class Page<T> {
    private final List<T> data;
    private final boolean hasMore;
    private final String nextCursor;
    private final Function<String, Page<T>> fetch;

    Page(List<T> data, boolean hasMore, String nextCursor, Function<String, Page<T>> fetch) {
        this.data = Collections.unmodifiableList(data);
        this.hasMore = hasMore;
        this.nextCursor = nextCursor;
        this.fetch = fetch;
    }

    /** Items of this page. */
    public List<T> data() {
        return data;
    }

    /** {@code has_more}: there are more items after this page. */
    public boolean hasMore() {
        return hasMore;
    }

    /** {@code next_cursor}: where the next page starts, or {@code null}. */
    public String nextCursor() {
        return nextCursor;
    }

    public boolean hasNextPage() {
        return hasMore && nextCursor != null && fetch != null;
    }

    /** Fetches the next page (same filters and limit). */
    public Page<T> nextPage() {
        if (!hasNextPage()) throw new NoSuchElementException("This is the last page");
        return fetch.apply(nextCursor);
    }

    /** All items of this and the following pages, fetched lazily. */
    public Iterable<T> autoPager() {
        return () -> new Iterator<T>() {
            private Page<T> page = Page.this;
            private int index;

            @Override
            public boolean hasNext() {
                while (index >= page.data.size()) {
                    if (!page.hasNextPage()) return false;
                    page = page.nextPage();
                    index = 0;
                }
                return true;
            }

            @Override
            public T next() {
                if (!hasNext()) throw new NoSuchElementException();
                return page.data.get(index++);
            }
        };
    }

    /** {@link #autoPager()} as a lazy sequential stream. */
    public Stream<T> stream() {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(autoPager().iterator(), Spliterator.ORDERED), false);
    }

    @Override
    public String toString() {
        return "Page{size=" + data.size() + ", hasMore=" + hasMore + ", nextCursor=" + nextCursor + "}";
    }
}
