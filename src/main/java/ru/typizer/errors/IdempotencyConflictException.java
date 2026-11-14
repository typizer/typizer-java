package ru.typizer.errors;

/** 409 {@code idempotency_conflict}: the {@code Idempotency-Key} was used with another request, or the first request with it is still running. */
public class IdempotencyConflictException extends ConflictException {
    private static final long serialVersionUID = 1L;

    public IdempotencyConflictException(Details details) {
        super(details);
    }
}
