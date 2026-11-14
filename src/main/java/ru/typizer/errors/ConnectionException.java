package ru.typizer.errors;

/** No answer from the API: DNS, TCP, TLS, a dropped connection. Retried automatically. */
public class ConnectionException extends TypizerException {
    private static final long serialVersionUID = 1L;

    public ConnectionException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public boolean isRetryable() {
        return true;
    }
}
