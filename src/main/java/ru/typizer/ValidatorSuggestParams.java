package ru.typizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What {@code validators().suggest(…)} drafts a validator from: a description in words, examples
 * that must pass and must fail, the schema field. A description or examples that must pass are
 * needed (the server answers {@code BadRequestException} otherwise).
 */
public final class ValidatorSuggestParams {
    final Map<String, Object> body;

    private ValidatorSuggestParams(Map<String, Object> body) {
        this.body = body;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String description;
        private List<Object> pass;
        private List<Object> fail;
        private Map<String, Object> field;

        private Builder() {}

        /** Which values are right, in words (up to 1000 characters). */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /** Values that must pass ({@code String}s or {@code Number}s, up to 50). */
        public Builder pass(Object... values) {
            this.pass = new ArrayList<>(Arrays.asList(values));
            return this;
        }

        /** Values that must fail (up to 50). */
        public Builder fail(Object... values) {
            this.fail = new ArrayList<>(Arrays.asList(values));
            return this;
        }

        /** The schema field the check is for: its name. */
        public Builder field(String name) {
            return field(name, null, null);
        }

        /** The schema field: name, description and JSON type ({@code string}, {@code number}, …); any may be null. */
        public Builder field(String name, String description, String type) {
            Map<String, Object> f = new LinkedHashMap<>();
            if (name != null) f.put("name", name);
            if (description != null) f.put("description", description);
            if (type != null) f.put("type", type);
            this.field = f;
            return this;
        }

        public ValidatorSuggestParams build() {
            Map<String, Object> b = new LinkedHashMap<>();
            if (description != null) b.put("description", description);
            if (pass != null || fail != null) {
                Map<String, Object> examples = new LinkedHashMap<>();
                if (pass != null) examples.put("pass", new ArrayList<>(pass));
                if (fail != null) examples.put("fail", new ArrayList<>(fail));
                b.put("examples", examples);
            }
            if (field != null) b.put("field", new LinkedHashMap<>(field));
            return new ValidatorSuggestParams(b);
        }
    }
}
