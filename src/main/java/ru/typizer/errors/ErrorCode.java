package ru.typizer.errors;

/**
 * Error codes of the API ({@link ApiException#code()}, {@code Problem.code}). The list is open: the
 * API may add codes, and {@code code()} returns them as they are. Each code has a page at
 * {@code https://docs.typizer.ru/errors/<code>}.
 */
public final class ErrorCode {
    public static final String INVALID_REQUEST = "invalid_request";
    public static final String INVALID_ID = "invalid_id";
    public static final String INVALID_SCHEMA = "invalid_schema";
    public static final String UNSUPPORTED_SCHEMA_FEATURE = "unsupported_schema_feature";
    public static final String FILE_TOO_LARGE = "file_too_large";
    public static final String UNSUPPORTED_FILE_TYPE = "unsupported_file_type";
    public static final String TOO_MANY_PAGES = "too_many_pages";
    public static final String UNAUTHORIZED = "unauthorized";
    public static final String FORBIDDEN = "forbidden";
    public static final String INSUFFICIENT_SCOPE = "insufficient_scope";
    public static final String NOT_FOUND = "not_found";
    public static final String CONFLICT = "conflict";
    public static final String IDEMPOTENCY_CONFLICT = "idempotency_conflict";
    public static final String INSUFFICIENT_BALANCE = "insufficient_balance";
    public static final String RATE_LIMITED = "rate_limited";
    public static final String EXTRACTION_FAILED = "extraction_failed";
    public static final String DOCUMENT_UNREADABLE = "document_unreadable";
    public static final String UPSTREAM_UNAVAILABLE = "upstream_unavailable";
    public static final String INTERNAL_ERROR = "internal_error";
    /** Run error only ({@code run.error.code}): processing took too long. */
    public static final String TIMEOUT = "timeout";

    private ErrorCode() {}
}
