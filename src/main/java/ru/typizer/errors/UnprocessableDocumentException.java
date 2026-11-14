package ru.typizer.errors;

/** 422 {@code document_unreadable} or {@code extraction_failed}. */
public class UnprocessableDocumentException extends ApiException {
    private static final long serialVersionUID = 1L;

    public UnprocessableDocumentException(Details details) {
        super(details);
    }
}
