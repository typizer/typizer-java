package ru.typizer.errors;

/** 503 {@code upstream_unavailable} (and 502, 504 from the network edge), after the automatic retries. */
public class ServiceUnavailableException extends ApiException {
    private static final long serialVersionUID = 1L;

    public ServiceUnavailableException(Details details) {
        super(details);
    }
}
