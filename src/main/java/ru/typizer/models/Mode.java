package ru.typizer.models;

/** Recognition mode of a run. Open: values this SDK does not know parse to {@link #UNKNOWN}. */
public enum Mode implements TypizerEnum {
    STANDARD("standard"),
    PRECISE("precise"),
    /** A value newer than this SDK. */
    UNKNOWN(null);

    private final String value;

    Mode(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The constant for an API value; {@link #UNKNOWN} for one this SDK does not know. */
    public static Mode of(String value) {
        return TypizerEnum.parse(Mode.class, value);
    }

    @Override
    public String toString() {
        return value == null ? "unknown" : value;
    }
}
