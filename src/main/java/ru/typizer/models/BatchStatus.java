package ru.typizer.models;

/** Status of a batch. Open: values this SDK does not know parse to {@link #UNKNOWN}. */
public enum BatchStatus implements TypizerEnum {
    PROCESSING("processing"),
    COMPLETED("completed"),
    /** A value newer than this SDK. */
    UNKNOWN(null);

    private final String value;

    BatchStatus(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The constant for an API value; {@link #UNKNOWN} for one this SDK does not know. */
    public static BatchStatus of(String value) {
        return TypizerEnum.parse(BatchStatus.class, value);
    }

    @Override
    public String toString() {
        return value == null ? "unknown" : value;
    }
}
