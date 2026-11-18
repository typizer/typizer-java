package ru.typizer.models;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The answer to {@code validators().update(…)}: the validator and what the change did to its version
 * ({@link #bump()}), with the typizers it moved and those offered the update.
 */
public final class ValidatorUpdateResult extends TypizerObject {
    static final Set<String> FIELDS = fields("object", "validator", "bump", "reasons", "propagated_to",
            "suggested_to");

    public ValidatorUpdateResult(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** The validator after the change. */
    public Validator validator() {
        return object("validator", Validator::new);
    }

    /** {@code major}, {@code minor}, {@code patch} or {@code none}. */
    public ValidatorBump bump() {
        return enumValue("bump", ValidatorBump.class);
    }

    /** The raw value of {@code bump} (also for values newer than this SDK). */
    public String bumpValue() {
        return str("bump");
    }

    /** Why this bump, in Russian. */
    public List<String> reasons() {
        return strings("reasons");
    }

    /** Typizers moved to the new version automatically. */
    public List<Propagation> propagatedTo() {
        return objects("propagated_to", Propagation::new);
    }

    /** Typizers ({@code tz_…}) offered the new major version. */
    public List<String> suggestedTo() {
        return strings("suggested_to");
    }

    /** A typizer moved to the new validator version, and its new version. */
    public static final class Propagation extends TypizerObject {
        static final Set<String> FIELDS = fields("typizer_id", "version");

        public Propagation(Map<String, Object> data) {
            super(data, FIELDS);
        }

        /** {@code tz_…}. */
        public String typizerId() {
            return str("typizer_id");
        }

        /** The typizer's new version. */
        public Integer version() {
            return integer("version");
        }
    }
}
