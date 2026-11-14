package ru.typizer.models;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** An API error body ({@code application/problem+json}, RFC 9457). */
public final class Problem extends TypizerObject {
    static final Set<String> FIELDS = fields("object", "type", "title", "status", "detail", "code", "param", "request_id", "doc_url", "batch_id", "created", "expected", "findings", "retry_after");

    public Problem(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** {@code type}. */
    public String type() {
        return str("type");
    }

    /** {@code title}. */
    public String title() {
        return str("title");
    }

    /** {@code status}. */
    public Integer status() {
        return integer("status");
    }

    /** {@code detail}. */
    public String detail() {
        return str("detail");
    }

    /** {@code code}. */
    public String code() {
        return str("code");
    }

    /** {@code param}. */
    public String param() {
        return str("param");
    }

    /** {@code request_id}. */
    public String requestId() {
        return str("request_id");
    }

    /** {@code doc_url}. */
    public String docUrl() {
        return str("doc_url");
    }

    /** {@code batch_id}. */
    public String batchId() {
        return str("batch_id");
    }

    /** {@code created}. */
    public Integer created() {
        return integer("created");
    }

    /** {@code expected}. */
    public Integer expected() {
        return integer("expected");
    }

    /** {@code findings}. */
    public List<Object> findings() {
        return list("findings");
    }

    /** {@code retry_after}. */
    public Integer retryAfter() {
        return integer("retry_after");
    }
}
