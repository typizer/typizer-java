package ru.typizer.models;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

/** An uploaded file ({@code file_…}); files are kept for a limited time. */
public final class File extends TypizerObject {
    static final Set<String> FIELDS = fields("object", "id", "filename", "mime", "size_bytes", "pages_estimate", "source", "expires_at", "created_at");

    public File(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** {@code id}. */
    public String id() {
        return str("id");
    }

    /** {@code filename}. */
    public String filename() {
        return str("filename");
    }

    /** {@code mime}. */
    public String mime() {
        return str("mime");
    }

    /** {@code size_bytes}. */
    public Long sizeBytes() {
        return longValue("size_bytes");
    }

    /** {@code pages_estimate}. */
    public Integer pagesEstimate() {
        return integer("pages_estimate");
    }

    /** {@code source}. */
    public String source() {
        return str("source");
    }

    /** {@code expires_at}. */
    public OffsetDateTime expiresAt() {
        return dateTime("expires_at");
    }

    /** {@code created_at}. */
    public OffsetDateTime createdAt() {
        return dateTime("created_at");
    }
}
