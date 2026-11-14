package ru.typizer.errors;

/**
 * One HTTP attempt took longer than the timeout ({@code timeout}, 75 s by default). Retried
 * automatically. Not to be confused with {@link WaitTimeoutException} (waiting for a run).
 */
public class TimeoutException extends ConnectionException {
    private static final long serialVersionUID = 1L;

    public TimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
