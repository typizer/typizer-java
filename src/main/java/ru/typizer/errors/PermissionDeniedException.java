package ru.typizer.errors;

/** 403 {@code forbidden} or {@code insufficient_scope}: the key may not do this. */
public class PermissionDeniedException extends ApiException {
    private static final long serialVersionUID = 1L;

    public PermissionDeniedException(Details details) {
        super(details);
    }
}
