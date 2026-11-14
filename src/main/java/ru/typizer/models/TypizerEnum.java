package ru.typizer.models;

/**
 * An open enumeration of API values. The API adds values without a new version, so every SDK enum
 * has {@code UNKNOWN}: a value this SDK does not know parses to it (the original string stays
 * available from the model, e.g. {@code run.statusValue()}) instead of failing.
 */
public interface TypizerEnum {
    /** The value on the wire ({@code "succeeded"}); {@code null} for {@code UNKNOWN}. */
    String value();

    /** The constant for {@code value}, {@code UNKNOWN} for a value this SDK does not know, {@code null} for null. */
    static <E extends Enum<E> & TypizerEnum> E parse(Class<E> type, String value) {
        if (value == null) return null;
        E unknown = null;
        for (E e : type.getEnumConstants()) {
            if (value.equals(e.value())) return e;
            if (e.value() == null) unknown = e;
        }
        return unknown;
    }
}
