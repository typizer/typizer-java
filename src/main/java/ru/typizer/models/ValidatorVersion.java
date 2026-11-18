package ru.typizer.models;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One version of a validator ({@code valv_…}): its spec, its tests and a plain explanation. */
public final class ValidatorVersion extends TypizerObject {
    static final Set<String> FIELDS = fields("object", "id", "version", "spec", "tests", "explain", "rule_preview",
            "changelog", "created_at");

    public ValidatorVersion(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** {@code valv_…}. */
    public String id() {
        return str("id");
    }

    /** {@code 1.0.0}. */
    public String version() {
        return str("version");
    }

    /**
     * The spec as JSON ({@code kind}, {@code applies_to}, {@code rules}, {@code message}, …) — a free
     * {@code Map}, so new kinds of rules never break parsing.
     */
    public Map<String, Object> spec() {
        return map("spec");
    }

    /** The version's contract: values that must pass and must fail. */
    public List<ValidatorTest> tests() {
        return objects("tests", ValidatorTest::new);
    }

    /** What the check does, in Russian. */
    public String explain() {
        return str("explain");
    }

    /** The rules, one line each. */
    public List<String> rulePreview() {
        return strings("rule_preview");
    }

    /** {@code changelog}. */
    public String changelog() {
        return str("changelog");
    }

    /** {@code created_at}. */
    public OffsetDateTime createdAt() {
        return dateTime("created_at");
    }

    @Override
    public String toString() {
        // Not the test values: they may come from documents.
        return "ValidatorVersion{id=" + id() + ", version=" + version() + ", tests=" + tests().size() + "}";
    }
}
