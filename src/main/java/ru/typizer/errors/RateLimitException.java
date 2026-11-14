package ru.typizer.errors;

/** 429 {@code rate_limited}, after the automatic retries. {@link #retryAfter()} says when to try again. */
public class RateLimitException extends ApiException {
    private static final long serialVersionUID = 1L;

    public RateLimitException(Details details) {
        super(details);
    }
}
