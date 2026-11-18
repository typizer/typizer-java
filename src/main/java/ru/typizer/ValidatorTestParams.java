package ru.typizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ru.typizer.internal.JsonText;

/**
 * What {@code validators().test(…)} runs: 1–100 values and exactly one of a reference
 * ({@code my/contract-no@1}, {@code inn@1}) or a spec. Nothing is saved.
 */
public final class ValidatorTestParams {
    final Map<String, Object> body;

    private ValidatorTestParams(Map<String, Object> body) {
        this.body = body;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private List<Object> values;
        private String ref;
        private Map<String, Object> spec;

        private Builder() {}

        /** The values to check: {@code String}s or {@code Number}s, 1–100. */
        public Builder values(Object... values) {
            return values(Arrays.asList(values));
        }

        public Builder values(List<?> values) {
            this.values = new ArrayList<>(values);
            return this;
        }

        /** A saved or official check: {@code my/contract-no@1}, {@code inn@1}, {@code val_…}. */
        public Builder ref(String ref) {
            this.ref = ref;
            return this;
        }

        /** A spec to try before saving it: a {@code Map}, JSON text or a Jackson/Gson tree node. */
        public Builder spec(Object spec) {
            this.spec = JsonText.toObject(spec, "spec");
            return this;
        }

        /** @throws IllegalArgumentException without values, with more than 100, or without exactly one of ref and spec */
        public ValidatorTestParams build() {
            if ((ref == null) == (spec == null))
                throw new IllegalArgumentException("Exactly one of ref and spec is required");
            if (values == null || values.isEmpty() || values.size() > 100)
                throw new IllegalArgumentException("values: 1–100 strings or numbers");
            for (Object v : values) {
                if (!(v instanceof String) && !(v instanceof Number))
                    throw new IllegalArgumentException("values are strings or numbers, not " + v);
            }
            Map<String, Object> b = new LinkedHashMap<>();
            if (ref != null) b.put("ref", ref);
            if (spec != null) b.put("spec", spec);
            b.put("values", new ArrayList<>(values));
            return new ValidatorTestParams(b);
        }
    }
}
