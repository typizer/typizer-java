package ru.typizer.models;

/** Output format of a run. Open: values this SDK does not know parse to {@link #UNKNOWN}. */
public enum Format implements TypizerEnum {
    JSON("json"),
    XML("xml"),
    CSV("csv"),
    XLSX("xlsx"),
    /** A value newer than this SDK. */
    UNKNOWN(null);

    private final String value;

    Format(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The constant for an API value; {@link #UNKNOWN} for one this SDK does not know. */
    public static Format of(String value) {
        return TypizerEnum.parse(Format.class, value);
    }

    @Override
    public String toString() {
        return value == null ? "unknown" : value;
    }
}
