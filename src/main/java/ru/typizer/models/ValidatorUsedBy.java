package ru.typizer.models;

import java.util.Map;
import java.util.Set;

/** A typizer that uses a validator ({@code ValidatorDetail.usedBy()}). */
public final class ValidatorUsedBy extends TypizerObject {
    static final Set<String> FIELDS = fields("typizer_id", "name", "version", "ref", "locked_version",
            "update_available");

    public ValidatorUsedBy(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** {@code tz_…}. */
    public String typizerId() {
        return str("typizer_id");
    }

    /** The name of the typizer. */
    public String name() {
        return str("name");
    }

    /** The typizer's version. */
    public Integer version() {
        return integer("version");
    }

    /** The reference in its schema: {@code my/contract-no@1}. */
    public String ref() {
        return str("ref");
    }

    /** The validator version the typizer is locked on: {@code 1.0.0}. */
    public String lockedVersion() {
        return str("locked_version");
    }

    /** A newer major version the typizer could move to, or {@code null}. */
    public String updateAvailable() {
        return str("update_available");
    }
}
