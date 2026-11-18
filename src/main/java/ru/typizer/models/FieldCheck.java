package ru.typizer.models;

import java.util.Map;
import java.util.Set;

/**
 * One check of a field ({@code FieldResult.checks()}): an official check ({@code inn}), your own
 * ({@code my/contract-no}), one written into the schema ({@code inline}) or a cross-document check
 * ({@code doc:<id>}), and how it went.
 */
public final class FieldCheck extends TypizerObject {
    static final Set<String> FIELDS = fields("ref", "version", "name", "status", "rule", "reason", "corrected");

    public FieldCheck(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** {@code inn}, {@code my/contract-no}, {@code inline} or {@code doc:<id>}. */
    public String ref() {
        return str("ref");
    }

    /** The exact version that ran ({@code 1.0.0}); {@code null} for an inline check. */
    public String version() {
        return str("version");
    }

    /** The name of the check ({@code ИНН}, {@code Номер договора}); {@code null} for an inline check. */
    public String name() {
        return str("name");
    }

    /** {@code pass}, {@code fail} or {@code skipped}. */
    public CheckStatus status() {
        return enumValue("status", CheckStatus.class);
    }

    /** The raw status (also for values newer than this SDK). */
    public String statusValue() {
        return str("status");
    }

    /** The rule that decided ({@code checksum}, {@code pattern}, {@code length}, …), if known. */
    public String rule() {
        return str("rule");
    }

    /**
     * Why it failed: {@code pattern_mismatch}, {@code checksum_mismatch}, {@code invalid_format}, …;
     * otherwise {@code null}.
     */
    public String reason() {
        return str("reason");
    }

    /**
     * {@code true} on a pass that failed at first: the field was read once more from the same
     * place of the document («Перечитать при ошибке», precise mode).
     */
    public boolean corrected() {
        return flag("corrected");
    }

    @Override
    public String toString() {
        return "FieldCheck{ref=" + ref() + (version() != null ? ", version=" + version() : "") + ", status="
                + statusValue() + (reason() != null ? ", reason=" + reason() : "") + "}";
    }
}
