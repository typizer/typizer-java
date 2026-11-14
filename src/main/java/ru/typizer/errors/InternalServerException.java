package ru.typizer.errors;

/** 500 {@code internal_error} (and other 5xx except 502–504), after the automatic retries. */
public class InternalServerException extends ApiException {
    private static final long serialVersionUID = 1L;

    public InternalServerException(Details details) {
        super(details);
    }
}
