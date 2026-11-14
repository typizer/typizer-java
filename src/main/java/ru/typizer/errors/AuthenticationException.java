package ru.typizer.errors;

/** 401 {@code unauthorized}: the API key is missing, wrong or revoked. */
public class AuthenticationException extends ApiException {
    private static final long serialVersionUID = 1L;

    public AuthenticationException(Details details) {
        super(details);
    }
}
