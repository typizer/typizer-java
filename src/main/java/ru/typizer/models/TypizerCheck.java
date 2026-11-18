package ru.typizer.models;

import java.util.Map;
import java.util.Set;

/** A check a typizer version is locked on ({@code Typizer.checks()}, {@code TypizerVersion.checks()}). */
public final class TypizerCheck extends TypizerObject {
    static final Set<String> FIELDS = fields("ref", "version", "name");

    public TypizerCheck(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** The reference without a version: {@code inn}, {@code my/contract-no}. */
    public String ref() {
        return str("ref");
    }

    /** The exact version: {@code 1.0.0}. */
    public String version() {
        return str("version");
    }

    /** The name of the check. */
    public String name() {
        return str("name");
    }
}
