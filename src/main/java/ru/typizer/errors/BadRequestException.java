package ru.typizer.errors;

/** 400: invalid request, id or schema ({@code invalid_request}, {@code invalid_id}, {@code invalid_schema}, {@code unsupported_schema_feature}, {@code too_many_pages}). Fix the request; a retry will not help. */
public class BadRequestException extends ApiException {
    private static final long serialVersionUID = 1L;

    public BadRequestException(Details details) {
        super(details);
    }
}
