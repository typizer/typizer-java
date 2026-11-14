package ru.typizer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ru.typizer.models.Format;
import ru.typizer.models.Mode;
import ru.typizer.models.Typizer;

/**
 * A batch: one typizer over many documents — either local files (sent in one
 * {@code multipart/form-data} request) or files uploaded before ({@code file_ids}), not both.
 */
public final class BatchParams {
    final String typizerId;
    final List<TypizerFile> files;
    final List<String> fileIds;
    final String format;
    final String mode;
    final Map<String, String> metadata;

    private BatchParams(Builder b) {
        this.typizerId = b.typizerId;
        this.files = Collections.unmodifiableList(new ArrayList<>(b.files));
        this.fileIds = Collections.unmodifiableList(new ArrayList<>(b.fileIds));
        this.format = b.format;
        this.mode = b.mode;
        this.metadata = b.metadata;
        if (typizerId == null || typizerId.isEmpty()) throw new IllegalArgumentException("typizer is required");
        if (files.isEmpty() == fileIds.isEmpty())
            throw new IllegalArgumentException("Pass either files (local documents) or fileIds (uploaded), not both or none");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String typizerId;
        private final List<TypizerFile> files = new ArrayList<>();
        private final List<String> fileIds = new ArrayList<>();
        private String format;
        private String mode;
        private Map<String, String> metadata;

        private Builder() {}

        public Builder typizer(String typizerId) {
            this.typizerId = typizerId;
            return this;
        }

        public Builder typizer(Typizer typizer) {
            this.typizerId = typizer.id();
            return this;
        }

        /** A document to send with the batch (path, bytes or stream). */
        public Builder file(TypizerFile file) {
            if (file.kind() == TypizerFile.Kind.ID) return fileId(file.value());
            if (!file.isUpload()) throw new IllegalArgumentException("A batch takes local files or uploaded file ids, not URLs");
            files.add(file);
            return this;
        }

        public Builder file(Path path) {
            return file(TypizerFile.fromPath(path));
        }

        /** A file uploaded with {@code files().upload()}. */
        public Builder fileId(String fileId) {
            fileIds.add(fileId);
            return this;
        }

        public Builder format(Format format) {
            this.format = ExtractParams.enumValue(format, "format");
            return this;
        }

        public Builder format(String format) {
            this.format = format;
            return this;
        }

        public Builder mode(Mode mode) {
            this.mode = ExtractParams.enumValue(mode, "mode");
            return this;
        }

        public Builder mode(String mode) {
            this.mode = mode;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata == null ? null : new LinkedHashMap<>(metadata);
            return this;
        }

        public BatchParams build() {
            return new BatchParams(this);
        }
    }
}
