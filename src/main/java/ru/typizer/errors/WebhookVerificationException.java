package ru.typizer.errors;

/**
 * {@code Webhooks.verify()}: the delivery is not from Typizer, or too old. Answer it with 400 and do
 * not process the body.
 */
public class WebhookVerificationException extends TypizerException {
    private static final long serialVersionUID = 1L;

    /** A {@code webhook-id}, {@code webhook-timestamp} or {@code webhook-signature} header is missing. */
    public static final String MISSING_HEADER = "missing_header";
    /** {@code webhook-timestamp} is not Unix seconds. */
    public static final String INVALID_TIMESTAMP = "invalid_timestamp";
    /** {@code webhook-timestamp} is further from now than the tolerance (5 minutes by default). */
    public static final String TIMESTAMP_OUT_OF_TOLERANCE = "timestamp_out_of_tolerance";
    /** No {@code v1} signature matches. */
    public static final String SIGNATURE_MISMATCH = "signature_mismatch";
    /** The secret is not {@code whsec_} + base64 of at least 24 bytes. */
    public static final String INVALID_SECRET = "invalid_secret";

    private final String reason;

    public WebhookVerificationException(String reason, String message) {
        super("[" + reason + "] " + message);
        this.reason = reason;
    }

    /** One of the constants of this class. */
    public String reason() {
        return reason;
    }
}
