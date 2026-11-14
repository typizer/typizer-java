package ru.typizer.errors;

/** 413 {@code file_too_large}: the file is larger than 50 MiB. */
public class FileTooLargeException extends ApiException {
    private static final long serialVersionUID = 1L;

    public FileTooLargeException(Details details) {
        super(details);
    }
}
