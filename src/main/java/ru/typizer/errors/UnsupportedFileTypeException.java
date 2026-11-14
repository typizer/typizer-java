package ru.typizer.errors;

/** 415 {@code unsupported_file_type}. */
public class UnsupportedFileTypeException extends ApiException {
    private static final long serialVersionUID = 1L;

    public UnsupportedFileTypeException(Details details) {
        super(details);
    }
}
