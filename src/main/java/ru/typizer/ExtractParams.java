package ru.typizer;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import ru.typizer.internal.JsonText;
import ru.typizer.models.Format;
import ru.typizer.models.Mode;
import ru.typizer.models.Typizer;

/**
 * A run to create: the document and exactly one of {@link Builder#schema schema},
 * {@link Builder#catalog catalog} or {@link Builder#typizer typizer}. Used by
 * {@code client.extract(params)} (create and wait) and {@code client.runs().create(params)}.
 *
 * <pre>{@code
 * Run run = client.extract(ExtractParams.builder()
 *         .file(Paths.get("invoice.pdf"))
 *         .catalog("schet-faktura@1")
 *         .metadata("order_id", "12345")
 *         .build());
 * }</pre>
 */
public final class ExtractParams {
    final TypizerFile file;
    final Map<String, Object> schema;
    final String catalog;
    final String typizerId;
    final String instructions;
    final String mode;
    final String format;
    final Map<String, String> metadata;
    final String webhookUrl;
    final Duration timeout;
    final Duration pollInterval;

    private ExtractParams(Builder b) {
        this.file = b.file;
        this.schema = b.schema;
        this.catalog = b.catalog;
        this.typizerId = b.typizerId;
        this.instructions = b.instructions;
        this.mode = b.mode;
        this.format = b.format;
        this.metadata = b.metadata == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(b.metadata));
        this.webhookUrl = b.webhookUrl;
        this.timeout = b.timeout;
        this.pollInterval = b.pollInterval;
        validate();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Argument errors are found here, before anything is sent (SPEC §3). */
    private void validate() {
        if (file == null) throw new IllegalArgumentException("file is required: TypizerFile.fromPath/fromBytes/fromStream/fromUrl/fromId");
        int sources = (schema != null ? 1 : 0) + (catalog != null ? 1 : 0) + (typizerId != null ? 1 : 0);
        if (sources != 1)
            throw new IllegalArgumentException("Exactly one of schema, catalog or typizer is required (got " + sources + ")");
        if (instructions != null && schema == null)
            throw new IllegalArgumentException("instructions go only with schema (a catalog schema or a typizer has its own)");
    }

    public static final class Builder {
        private TypizerFile file;
        private Map<String, Object> schema;
        private String catalog;
        private String typizerId;
        private String instructions;
        private String mode;
        private String format;
        private Map<String, String> metadata;
        private String webhookUrl;
        private Duration timeout;
        private Duration pollInterval;

        private Builder() {}

        /** The document. */
        public Builder file(TypizerFile file) {
            this.file = file;
            return this;
        }

        /** A local file. */
        public Builder file(Path path) {
            return file(TypizerFile.fromPath(path));
        }

        /** Bytes and a file name. */
        public Builder file(byte[] bytes, String filename) {
            return file(TypizerFile.fromBytes(bytes, filename));
        }

        /** A stream and a file name. */
        public Builder file(InputStream stream, String filename) {
            return file(TypizerFile.fromStream(stream, filename));
        }

        /** A public URL of the document. */
        public Builder url(URL url) {
            return file(TypizerFile.fromUrl(url));
        }

        /** A public URL of the document. */
        public Builder url(String url) {
            return file(TypizerFile.fromUrl(url));
        }

        /** A file uploaded before ({@code file_…}). */
        public Builder fileId(String fileId) {
            return file(TypizerFile.fromId(fileId));
        }

        /**
         * A one-off JSON Schema ({@code {"type": "object", "properties": …}}): a {@code Map} (use a
         * {@code LinkedHashMap} — the order of properties is the order of columns and XML elements),
         * JSON text, or a Jackson/Gson tree node.
         */
        public Builder schema(Object schema) {
            this.schema = schema == null ? null : JsonText.toObject(schema, "schema");
            return this;
        }

        /** A catalog schema: {@code "schet-faktura"}, {@code "schet-faktura@1"}, {@code "acme/akt-sverki@1.2.0"}. */
        public Builder catalog(String ref) {
            this.catalog = ref;
            return this;
        }

        /** A saved typizer ({@code tz_…}). */
        public Builder typizer(String typizerId) {
            this.typizerId = typizerId;
            return this;
        }

        /** A saved typizer. */
        public Builder typizer(Typizer typizer) {
            this.typizerId = typizer == null ? null : typizer.id();
            return this;
        }

        /** Extra instructions for a one-off schema. */
        public Builder instructions(String instructions) {
            this.instructions = instructions;
            return this;
        }

        public Builder mode(Mode mode) {
            this.mode = enumValue(mode, "mode");
            return this;
        }

        /** {@code standard} or {@code precise}. */
        public Builder mode(String mode) {
            this.mode = mode;
            return this;
        }

        public Builder format(Format format) {
            this.format = enumValue(format, "format");
            return this;
        }

        /** {@code json}, {@code xml}, {@code csv} or {@code xlsx}. */
        public Builder format(String format) {
            this.format = format;
            return this;
        }

        /** Your data for the run (returned with it and in webhooks). Replaces earlier metadata. */
        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata == null ? null : new LinkedHashMap<>(metadata);
            return this;
        }

        /** One metadata entry. */
        public Builder metadata(String key, String value) {
            if (metadata == null) metadata = new LinkedHashMap<>();
            metadata.put(key, value);
            return this;
        }

        /** A URL to POST the finished run to (a one-off webhook). */
        public Builder webhookUrl(String webhookUrl) {
            this.webhookUrl = webhookUrl;
            return this;
        }

        /** {@code extract()} only: how long to wait for the result, 300 s by default. */
        public Builder timeout(Duration timeout) {
            if (timeout != null && timeout.isNegative()) throw new IllegalArgumentException("timeout must not be negative");
            this.timeout = timeout;
            return this;
        }

        /** {@code extract()} only: first pause between polls, 1 s by default (then ×1.5, up to 5 s). */
        public Builder pollInterval(Duration pollInterval) {
            if (pollInterval != null && (pollInterval.isNegative() || pollInterval.isZero()))
                throw new IllegalArgumentException("pollInterval must be positive");
            this.pollInterval = pollInterval;
            return this;
        }

        /** @throws IllegalArgumentException if the document or the schema source is missing or ambiguous */
        public ExtractParams build() {
            return new ExtractParams(this);
        }
    }

    static String enumValue(ru.typizer.models.TypizerEnum e, String what) {
        if (e == null) return null;
        if (e.value() == null) throw new IllegalArgumentException(what + " UNKNOWN cannot be sent");
        return e.value();
    }
}
