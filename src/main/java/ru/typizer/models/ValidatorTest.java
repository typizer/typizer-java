package ru.typizer.models;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A test of a validator version: a value and whether it must pass. Also what you pass to
 * {@code ValidatorParams.Builder.tests(…)}: {@link #pass(Object)}, {@link #fail(Object)}.
 */
public final class ValidatorTest extends TypizerObject {
    static final Set<String> FIELDS = fields("value", "expect", "note");

    /** {@code expect} of a value that must pass. */
    public static final String EXPECT_PASS = "pass";
    /** {@code expect} of a value that must fail. */
    public static final String EXPECT_FAIL = "fail";

    public ValidatorTest(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** A value that must pass (a {@code String} or a {@code Number}). */
    public static ValidatorTest pass(Object value) {
        return of(value, EXPECT_PASS, null);
    }

    /** A value that must fail (a {@code String} or a {@code Number}). */
    public static ValidatorTest fail(Object value) {
        return of(value, EXPECT_FAIL, null);
    }

    /** A test with a note; {@code expect} is {@code pass} or {@code fail}. */
    public static ValidatorTest of(Object value, String expect, String note) {
        if (!(value instanceof String) && !(value instanceof Number))
            throw new IllegalArgumentException("A test value is a String or a Number");
        if (expect == null) throw new IllegalArgumentException("expect is required: pass or fail");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", value);
        m.put("expect", expect);
        if (note != null) m.put("note", note);
        return new ValidatorTest(m);
    }

    /** The value: a {@code String}, or a number ({@code Long}/{@link java.math.BigDecimal}). */
    public Object value() {
        return get("value");
    }

    /** {@code pass} or {@code fail} (a newer value is kept as is). */
    public String expect() {
        return str("expect");
    }

    /** {@code note}. */
    public String note() {
        return str("note");
    }

    @Override
    public String toString() {
        // Never the value: it may come from a document.
        return "ValidatorTest{expect=" + expect() + "}";
    }
}
