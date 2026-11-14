package ru.typizer.models;

/** Type of a webhook event. Open: values this SDK does not know parse to {@link #UNKNOWN}. */
public enum WebhookEventType implements TypizerEnum {
    RUN_SUCCEEDED("run.succeeded"),
    RUN_FAILED("run.failed"),
    BATCH_COMPLETED("batch.completed"),
    BALANCE_LOW("balance.low"),
    /** A value newer than this SDK. */
    UNKNOWN(null);

    private final String value;

    WebhookEventType(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The constant for an API value; {@link #UNKNOWN} for one this SDK does not know. */
    public static WebhookEventType of(String value) {
        return TypizerEnum.parse(WebhookEventType.class, value);
    }

    @Override
    public String toString() {
        return value == null ? "unknown" : value;
    }
}
