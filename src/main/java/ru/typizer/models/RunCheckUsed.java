package ru.typizer.models;

import java.util.Map;
import java.util.Set;

/**
 * A check used by a run of a one-off schema ({@code run.checksUsed()}): a reference on its exact
 * version, or a check written into the schema ({@code ref = "inline"}, with {@link #field()}).
 */
public final class RunCheckUsed extends TypizerObject {
    static final Set<String> FIELDS = fields("ref", "version", "name", "field");

    public RunCheckUsed(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** {@code my/contract-no}, {@code inn}, or {@code inline}. */
    public String ref() {
        return str("ref");
    }

    /** The exact version ({@code 1.0.0}); {@code null} for an inline check. */
    public String version() {
        return str("version");
    }

    /** The name; {@code null} for an inline check. */
    public String name() {
        return str("name");
    }

    /** The schema field of an inline check ({@code spec_no}, {@code items[].code}); otherwise {@code null}. */
    public String field() {
        return str("field");
    }
}
