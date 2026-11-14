package ru.typizer.models;

/** Check status of one field of a run ({@code FieldResult.status}). Open: values this SDK does not know parse to {@link #UNKNOWN}. */
public enum FieldStatus implements TypizerEnum {
    OK("ok"),
    WARN("warn"),
    MISSING("missing"),
    /** A value newer than this SDK. */
    UNKNOWN(null);

    private final String value;

    FieldStatus(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The constant for an API value; {@link #UNKNOWN} for one this SDK does not know. */
    public static FieldStatus of(String value) {
        return TypizerEnum.parse(FieldStatus.class, value);
    }

    @Override
    public String toString() {
        return value == null ? "unknown" : value;
    }
}
