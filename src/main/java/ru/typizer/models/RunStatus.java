package ru.typizer.models;

/** Status of a run ({@code Run.status}). Open: values this SDK does not know parse to {@link #UNKNOWN}. */
public enum RunStatus implements TypizerEnum {
    QUEUED("queued"),
    PROCESSING("processing"),
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    /** A value newer than this SDK. */
    UNKNOWN(null);

    private final String value;

    RunStatus(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The constant for an API value; {@link #UNKNOWN} for one this SDK does not know. */
    public static RunStatus of(String value) {
        return TypizerEnum.parse(RunStatus.class, value);
    }

    @Override
    public String toString() {
        return value == null ? "unknown" : value;
    }
}
