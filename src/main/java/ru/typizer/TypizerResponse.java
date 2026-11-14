package ru.typizer;

import java.util.List;
import java.util.Map;

import ru.typizer.errors.ApiException;

/**
 * The value of a call together with the HTTP status and headers of its (last) response:
 * {@code X-Request-Id}, {@code RateLimit-*}, {@code Idempotent-Replayed}, {@code Typizer-Maintenance},
 * {@code Location}. Get it with {@link TypizerClient#withRawResponse}:
 *
 * <pre>{@code
 * TypizerResponse<Run> r = client.withRawResponse(c -> c.runs().get(runId));
 * System.out.println(r.requestId() + " " + r.data().status());
 * }</pre>
 */
public final class TypizerResponse<T> {
    private final T data;
    private final int statusCode;
    private final Map<String, List<String>> headers;

    TypizerResponse(T data, int statusCode, Map<String, List<String>> headers) {
        this.data = data;
        this.statusCode = statusCode;
        this.headers = headers;
    }

    /** What the call returned. */
    public T data() {
        return data;
    }

    public int statusCode() {
        return statusCode;
    }

    /** Headers (names case-insensitive). */
    public Map<String, List<String>> headers() {
        return headers;
    }

    /** The first value of a header, or {@code null}. */
    public String header(String name) {
        List<String> v = headers.get(name);
        return v == null || v.isEmpty() ? null : v.get(0);
    }

    /** {@code X-Request-Id}. */
    public String requestId() {
        return header("X-Request-Id");
    }

    /** {@code Idempotent-Replayed: true}: the API returned the stored result of an earlier call with the same key. */
    public boolean idempotentReplayed() {
        return "true".equalsIgnoreCase(header("Idempotent-Replayed"));
    }

    /** {@code RateLimit-*}, or {@code null}. */
    public ApiException.RateLimit rateLimit() {
        return ApiException.RateLimit.from(headers);
    }

    /** {@code Typizer-Maintenance} ({@code mnt_…; starts=…; ends=…}), or {@code null}. */
    public String maintenance() {
        return header("Typizer-Maintenance");
    }

    @Override
    public String toString() {
        return "TypizerResponse{status=" + statusCode + ", requestId=" + requestId() + "}";
    }
}
