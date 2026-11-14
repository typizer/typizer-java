package ru.typizer.errors;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import ru.typizer.internal.Json;
import ru.typizer.internal.RetryAfter;
import ru.typizer.models.Problem;

/**
 * The API answered with an error (4xx/5xx). The body is RFC 9457 {@code application/problem+json}
 * ({@link #problem()}); when it is not (a proxy or the network edge answered), the class and
 * {@link #code()} follow the HTTP status and {@link #detail()} is the start of the body.
 *
 * <p>The message reads {@code [insufficient_balance] Not enough pages… (HTTP 402, request_id=req_…,
 * https://docs.typizer.ru/errors/insufficient_balance)}. Give {@link #requestId()} to support.
 */
public class ApiException extends TypizerException {
    private static final long serialVersionUID = 1L;

    private static final Map<String, Integer> CODE_STATUS = new HashMap<>();

    static {
        String[][] table = {
            {"invalid_request", "400"}, {"invalid_id", "400"}, {"invalid_schema", "400"},
            {"unsupported_schema_feature", "400"}, {"too_many_pages", "400"}, {"unauthorized", "401"},
            {"insufficient_balance", "402"}, {"forbidden", "403"}, {"insufficient_scope", "403"},
            {"not_found", "404"}, {"conflict", "409"}, {"idempotency_conflict", "409"},
            {"file_too_large", "413"}, {"unsupported_file_type", "415"}, {"document_unreadable", "422"},
            {"extraction_failed", "422"}, {"rate_limited", "429"}, {"internal_error", "500"},
            {"upstream_unavailable", "503"},
        };
        for (String[] row : table) CODE_STATUS.put(row[0], Integer.valueOf(row[1]));
    }

    private final transient Details details;

    protected ApiException(Details details) {
        super(details.message());
        this.details = details;
    }

    /** HTTP status. */
    public int statusCode() {
        return details.status;
    }

    /**
     * Machine-readable code ({@code insufficient_balance}, {@code rate_limited}, …; see
     * {@link ErrorCode}). Codes newer than this SDK come through as they are.
     */
    public String code() {
        return details.code;
    }

    /** {@code title} of the problem, or {@code null}. */
    public String title() {
        return details.problem != null ? details.problem.title() : null;
    }

    /** What happened in this case. */
    public String detail() {
        return details.detail;
    }

    /** {@code type} of the problem (a URI), or {@code null}. */
    public String type() {
        return details.problem != null ? details.problem.type() : null;
    }

    /** The page about this code: {@code https://docs.typizer.ru/errors/<code>}. */
    public String docUrl() {
        return details.docUrl;
    }

    /** The request field at fault, if any. */
    public String param() {
        return details.problem != null ? details.problem.param() : null;
    }

    /** {@code request_id} of the problem, else the {@code X-Request-Id} header; {@code null} if neither. */
    public String requestId() {
        return details.requestId;
    }

    /** {@code Retry-After} (429/503), or {@code null}. */
    public Duration retryAfter() {
        return details.retryAfter;
    }

    /** {@code RateLimit-*} headers of the response, or {@code null} if it had none. */
    public RateLimit rateLimit() {
        return RateLimit.from(details.headers);
    }

    /** Response headers (names case-insensitive). */
    public Map<String, List<String>> headers() {
        return details.headers;
    }

    /** The parsed problem body, or {@code null} if the body was not {@code application/problem+json}. */
    public Problem problem() {
        return details.problem;
    }

    /** The response body as text (for a non-JSON body: what the proxy or edge said). */
    public String rawBody() {
        return details.rawBody;
    }

    /** Other fields of the problem: {@code batch_id}, {@code created}, {@code expected}, {@code findings}, … */
    public Map<String, Object> extra() {
        return details.problem != null ? details.problem.extra() : Collections.<String, Object>emptyMap();
    }

    /** 408, 429 and 5xx: the call may succeed later (and was retried automatically). */
    @Override
    public boolean isRetryable() {
        int s = details.status;
        return s == 408 || s == 429 || s >= 500;
    }

    /**
     * Builds the right subclass for a response — by {@code code} of the problem, else by status. Used
     * by the client; public for custom transports and tests.
     */
    public static ApiException from(int status, Map<String, List<String>> headers, String body) {
        Map<String, List<String>> h = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (headers != null) {
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                if (e.getKey() != null) h.put(e.getKey(), e.getValue());
            }
        }
        Problem problem = null;
        if (body != null && !body.isEmpty()) {
            try {
                Object parsed = Json.parse(body);
                if (parsed instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = (Map<String, Object>) parsed;
                    if (m.get("code") instanceof String || m.get("title") instanceof String) problem = new Problem(m);
                }
            } catch (IllegalArgumentException notJson) {
                // A proxy, the network edge or an HTML page: classified by status below.
            }
        }
        Details d = new Details(status, Collections.unmodifiableMap(h), problem, body);
        return create(d);
    }

    private static ApiException create(Details d) {
        if (d.status == 408) return new ApiException(d);
        String code = d.code;
        Integer byCode = CODE_STATUS.get(code);
        if ("idempotency_conflict".equals(code)) return new IdempotencyConflictException(d);
        if ("upstream_unavailable".equals(code)) return new ServiceUnavailableException(d);
        int s = byCode != null ? byCode : d.status;
        switch (s) {
            case 400: return new BadRequestException(d);
            case 401: return new AuthenticationException(d);
            case 402: return new InsufficientBalanceException(d);
            case 403: return new PermissionDeniedException(d);
            case 404: return new NotFoundException(d);
            case 409: return new ConflictException(d);
            case 413: return new FileTooLargeException(d);
            case 415: return new UnsupportedFileTypeException(d);
            case 422: return new UnprocessableDocumentException(d);
            case 429: return new RateLimitException(d);
            case 502:
            case 503:
            case 504: return new ServiceUnavailableException(d);
            default:
                if (s >= 500) return new InternalServerException(d);
                return new ApiException(d);
        }
    }

    /** {@code code} for a response without a problem body. */
    static String codeForStatus(int status) {
        switch (status) {
            case 400:
            case 408: return "invalid_request";
            case 401: return "unauthorized";
            case 402: return "insufficient_balance";
            case 403: return "forbidden";
            case 404: return "not_found";
            case 409: return "conflict";
            case 413: return "file_too_large";
            case 415: return "unsupported_file_type";
            case 422: return "extraction_failed";
            case 429: return "rate_limited";
            case 502:
            case 503:
            case 504: return "upstream_unavailable";
            default: return status >= 500 ? "internal_error" : "http_" + status;
        }
    }

    /** What the client knows about an error response. */
    public static final class Details {
        final int status;
        final Map<String, List<String>> headers;
        final Problem problem;
        final String rawBody;
        final String code;
        final String detail;
        final String requestId;
        final String docUrl;
        final Duration retryAfter;

        Details(int status, Map<String, List<String>> headers, Problem problem, String rawBody) {
            this.status = status;
            this.headers = headers;
            this.problem = problem;
            this.rawBody = rawBody == null ? "" : rawBody;
            String c = problem != null ? problem.code() : null;
            this.code = c != null ? c : codeForStatus(status);
            String det = problem != null ? problem.detail() : null;
            if (det == null && problem != null) det = problem.title();
            if (det == null) {
                String text = this.rawBody.trim();
                det = text.length() > 200 ? text.substring(0, 200) : text;
                if (det.isEmpty()) det = "HTTP " + status;
            }
            this.detail = det;
            String rid = problem != null ? problem.requestId() : null;
            if (rid == null || rid.isEmpty()) rid = first(headers, "X-Request-Id");
            this.requestId = rid == null || rid.isEmpty() ? null : rid;
            String doc = problem != null ? problem.docUrl() : null;
            if (doc == null && CODE_STATUS.containsKey(this.code)) doc = "https://docs.typizer.ru/errors/" + this.code;
            this.docUrl = doc;
            this.retryAfter = RetryAfter.parse(first(headers, "Retry-After"));
        }

        String message() {
            StringBuilder sb = new StringBuilder();
            sb.append('[').append(code).append("] ").append(detail).append(" (HTTP ").append(status);
            if (requestId != null) sb.append(", request_id=").append(requestId);
            if (docUrl != null) sb.append(", ").append(docUrl);
            return sb.append(')').toString();
        }
    }

    static String first(Map<String, List<String>> headers, String name) {
        List<String> v = headers.get(name);
        return v == null || v.isEmpty() ? null : v.get(0);
    }

    /** {@code RateLimit-Limit}, {@code RateLimit-Remaining}, {@code RateLimit-Reset} of a response. */
    public static final class RateLimit {
        private final Long limit;
        private final Long remaining;
        private final Long reset;

        private RateLimit(Long limit, Long remaining, Long reset) {
            this.limit = limit;
            this.remaining = remaining;
            this.reset = reset;
        }

        /** The headers of a response, or {@code null} if there were none. */
        public static RateLimit from(Map<String, List<String>> headers) {
            Long l = number(first(headers, "RateLimit-Limit"));
            Long r = number(first(headers, "RateLimit-Remaining"));
            Long s = number(first(headers, "RateLimit-Reset"));
            return l == null && r == null && s == null ? null : new RateLimit(l, r, s);
        }

        private static Long number(String v) {
            try {
                return v == null ? null : Long.valueOf(v.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        public Long limit() {
            return limit;
        }

        public Long remaining() {
            return remaining;
        }

        /** Seconds until the window resets. */
        public Long reset() {
            return reset;
        }

        @Override
        public String toString() {
            Map<String, Long> m = new LinkedHashMap<>();
            m.put("limit", limit);
            m.put("remaining", remaining);
            m.put("reset", reset);
            return "RateLimit" + m;
        }
    }
}
