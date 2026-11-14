package ru.typizer;

import java.util.LinkedHashMap;
import java.util.Map;

import ru.typizer.internal.JsonText;
import ru.typizer.models.Format;
import ru.typizer.models.Mode;

/**
 * Fields of a typizer for {@code typizers().create(…)} (name and schema required) and
 * {@code typizers().update(id, …)} (only the fields you set change).
 */
public final class TypizerParams {
    final Map<String, Object> body;

    private TypizerParams(Map<String, Object> body) {
        this.body = body;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, Object> b = new LinkedHashMap<>();

        private Builder() {}

        public Builder name(String name) {
            b.put("name", name);
            return this;
        }

        /** A description; {@code null} clears it on update. */
        public Builder description(String description) {
            b.put("description", description);
            return this;
        }

        /** The JSON Schema: a {@code Map} (keep its order with {@code LinkedHashMap}), JSON text or a tree node. */
        public Builder schema(Object schema) {
            b.put("schema", JsonText.toObject(schema, "schema"));
            return this;
        }

        public Builder instructions(String instructions) {
            b.put("instructions", instructions);
            return this;
        }

        public Builder defaultFormat(Format format) {
            b.put("default_format", ExtractParams.enumValue(format, "defaultFormat"));
            return this;
        }

        public Builder defaultFormat(String format) {
            b.put("default_format", format);
            return this;
        }

        public Builder mode(Mode mode) {
            b.put("mode", ExtractParams.enumValue(mode, "mode"));
            return this;
        }

        public Builder mode(String mode) {
            b.put("mode", mode);
            return this;
        }

        public TypizerParams build() {
            return new TypizerParams(new LinkedHashMap<>(b));
        }
    }
}
