package ru.typizer.models;

import java.util.Objects;

/**
 * A field whose value failed a check ({@code run.warnings()}): its path, the message to show a person
 * and the reason code. Built from {@link FieldResult}, without a request.
 */
public final class FieldWarning {
    private final String path;
    private final String message;
    private final String reason;

    public FieldWarning(String path, String message, String reason) {
        this.path = path;
        this.message = message;
        this.reason = reason;
    }

    /** The path of the field: {@code contract_no}, {@code items[0].amount}. */
    public String path() {
        return path;
    }

    /**
     * What is wrong, in Russian, ready to show ({@code «Номер договора не по шаблону ДГ-ГГГГ/NNNN»});
     * {@code null} in runs and webhooks made before the API sent it.
     */
    public String message() {
        return message;
    }

    /** The reason code: {@code pattern_mismatch}, {@code checksum_mismatch}, {@code sum_mismatch}, … */
    public String reason() {
        return reason;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FieldWarning)) return false;
        FieldWarning w = (FieldWarning) o;
        return Objects.equals(path, w.path) && Objects.equals(message, w.message) && Objects.equals(reason, w.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, message, reason);
    }

    @Override
    public String toString() {
        // The message is the check's own text, never the field value.
        return "FieldWarning{path=" + path + ", reason=" + reason + ", message=" + message + "}";
    }
}
