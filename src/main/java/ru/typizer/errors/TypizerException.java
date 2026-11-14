package ru.typizer.errors;

/**
 * Base of everything the SDK throws (unchecked). Mistakes in arguments — no schema, two documents at
 * once — are {@link IllegalArgumentException} instead: they are bugs in the calling code, not
 * failures of the service.
 */
public class TypizerException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public TypizerException(String message) {
        super(message);
    }

    public TypizerException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Whether the same call may succeed later (the SDK has already retried it automatically if it could). */
    public boolean isRetryable() {
        return false;
    }
}
