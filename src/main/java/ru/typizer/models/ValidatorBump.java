package ru.typizer.models;

/**
 * What a change of a validator did to its version ({@code ValidatorUpdateResult.bump}). Open: values
 * this SDK does not know parse to {@link #UNKNOWN} (the original string is
 * {@link ValidatorUpdateResult#bumpValue()}).
 */
public enum ValidatorBump implements TypizerEnum {
    /** A test was removed or broken, or the value type changed: typizers stay on the old version. */
    MAJOR("major"),
    /** Rules or tests changed and every old test still passes. */
    MINOR("minor"),
    /** Only the name or the description changed. */
    PATCH("patch"),
    /** Nothing changed: no new version. */
    NONE("none"),
    /** A value newer than this SDK. */
    UNKNOWN(null);

    private final String value;

    ValidatorBump(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The constant for an API value; {@link #UNKNOWN} for one this SDK does not know. */
    public static ValidatorBump of(String value) {
        return TypizerEnum.parse(ValidatorBump.class, value);
    }

    @Override
    public String toString() {
        return value == null ? "unknown" : value;
    }
}
