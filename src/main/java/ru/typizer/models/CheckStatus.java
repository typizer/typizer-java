package ru.typizer.models;

/**
 * Result of one check of a field ({@code FieldCheck.status}). Open: values this SDK does not know
 * parse to {@link #UNKNOWN} (the original string is {@link FieldCheck#statusValue()}).
 */
public enum CheckStatus implements TypizerEnum {
    PASS("pass"),
    FAIL("fail"),
    /** The check did not apply (an empty value, a value of another type). */
    SKIPPED("skipped"),
    /** A value newer than this SDK. */
    UNKNOWN(null);

    private final String value;

    CheckStatus(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The constant for an API value; {@link #UNKNOWN} for one this SDK does not know. */
    public static CheckStatus of(String value) {
        return TypizerEnum.parse(CheckStatus.class, value);
    }

    @Override
    public String toString() {
        return value == null ? "unknown" : value;
    }
}
