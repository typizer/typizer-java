package ru.typizer.models;

import java.util.Map;
import java.util.Set;

/** What a catalog validator gives a schema field: the reference and the rule. */
public final class CatalogValidatorBrief extends TypizerObject {
    static final Set<String> FIELDS = fields("ref", "spec");

    public CatalogValidatorBrief(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** What to write in a field's {@code x-typizer-check}: {@code inn@1}. */
    public String ref() {
        return str("ref");
    }

    /** The rule as it is ({@code kind: "rules"} or {@code "native"}). */
    public Map<String, Object> spec() {
        return map("spec");
    }
}
