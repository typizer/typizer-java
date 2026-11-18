package ru.typizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ru.typizer.internal.JsonText;
import ru.typizer.models.TypizerObject;
import ru.typizer.models.ValidatorTest;

/**
 * Fields of a validator for {@code validators().create(…)} (name and spec required) and
 * {@code validators().update(id, …)} (only the fields you set change; a new version when the spec or
 * the tests change).
 *
 * <pre>{@code
 * Map<String, Object> spec = new LinkedHashMap<>();   // or JSON text, or a Jackson/Gson node
 * spec.put("kind", "rules");
 * spec.put("applies_to", "string");
 * spec.put("rules", Collections.singletonList(Collections.singletonMap("type", "pattern")));
 * ValidatorParams.builder().name("Номер договора").spec(spec)
 *         .tests(ValidatorTest.pass("ДГ-2026/0153"), ValidatorTest.fail("ДГ-26/153")).build();
 * }</pre>
 */
public final class ValidatorParams {
    final Map<String, Object> body;

    private ValidatorParams(Map<String, Object> body) {
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

        /** The reference becomes {@code my/<slug>}; by default it comes from the name (create only). */
        public Builder slug(String slug) {
            b.put("slug", slug);
            return this;
        }

        /** A description; {@code null} clears it on update. */
        public Builder description(String description) {
            b.put("description", description);
            return this;
        }

        /**
         * The spec ({@code kind}, {@code applies_to}, {@code rules}, {@code message}, …): a {@code Map},
         * JSON text or a Jackson/Gson tree node. Sent as is; the server checks it
         * ({@code BadRequestException} with {@code param} like {@code spec#/rules/0/pattern}).
         */
        public Builder spec(Object spec) {
            b.put("spec", JsonText.toObject(spec, "spec"));
            return this;
        }

        /**
         * The version's contract (up to 100): {@link ValidatorTest#pass(Object)},
         * {@link ValidatorTest#fail(Object)}. Every test must hold, or the server answers
         * {@code BadRequestException} with {@code param} {@code tests/<i>}. On update the list replaces
         * the old one.
         */
        public Builder tests(ValidatorTest... tests) {
            return tests(Arrays.asList(tests));
        }

        /** Tests as {@link ValidatorTest}s or as maps ({@code value}, {@code expect}, {@code note}). */
        public Builder tests(List<?> tests) {
            List<Object> out = new ArrayList<>();
            for (Object t : tests) {
                if (t instanceof TypizerObject) out.add(((TypizerObject) t).toMap());
                else if (t instanceof Map) out.add(JsonText.toObject(t, "test"));
                else throw new IllegalArgumentException("A test is a ValidatorTest or a Map, not " + t);
            }
            b.put("tests", out);
            return this;
        }

        /** What changed — for the history of versions. */
        public Builder changelog(String changelog) {
            b.put("changelog", changelog);
            return this;
        }

        public ValidatorParams build() {
            return new ValidatorParams(new LinkedHashMap<>(b));
        }
    }
}
