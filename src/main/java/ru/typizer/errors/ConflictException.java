package ru.typizer.errors;

/** 409 {@code conflict}: the object is not in a state that allows this. */
public class ConflictException extends ApiException {
    private static final long serialVersionUID = 1L;

    public ConflictException(Details details) {
        super(details);
    }
}
