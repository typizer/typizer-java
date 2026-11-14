package ru.typizer.errors;

/** 404 {@code not_found}. */
public class NotFoundException extends ApiException {
    private static final long serialVersionUID = 1L;

    public NotFoundException(Details details) {
        super(details);
    }
}
