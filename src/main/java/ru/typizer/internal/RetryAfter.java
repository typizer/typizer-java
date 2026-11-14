package ru.typizer.internal;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** {@code Retry-After}: delta seconds or an HTTP date (RFC 9110 §10.2.3). */
public final class RetryAfter {
    private RetryAfter() {}

    /** The delay, or {@code null} if the header is absent or unreadable. */
    public static Duration parse(String value) {
        return parse(value, Instant.now());
    }

    static Duration parse(String value, Instant now) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;
        if (v.chars().allMatch(Character::isDigit)) {
            try {
                return Duration.ofSeconds(Long.parseLong(v));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        try {
            Instant at = ZonedDateTime.parse(v, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            Duration d = Duration.between(now, at);
            return d.isNegative() ? Duration.ZERO : d;
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
