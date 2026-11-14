package ru.typizer.models;

import java.util.Map;
import java.util.Set;

/** A typizer proposed by {@code typizers().generate(…)} — not saved yet. */
public final class TypizerDraft extends TypizerObject {
    static final Set<String> FIELDS = fields("object", "name", "schema", "instructions");

    public TypizerDraft(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** {@code name}. */
    public String name() {
        return str("name");
    }

    /** {@code schema}. */
    public Map<String, Object> schema() {
        return map("schema");
    }

    /** {@code instructions}. */
    public String instructions() {
        return str("instructions");
    }
}
