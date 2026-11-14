package ru.typizer.models;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import ru.typizer.internal.Json;

/**
 * Base of every API object: an immutable view over the JSON the API returned.
 *
 * <p>Accessors are named after the API fields in camelCase ({@code created_at} → {@code createdAt()})
 * and never throw on a missing or {@code null} field — they return {@code null} (or an empty
 * collection). Fields that this version of the SDK does not know yet are kept: {@link #extra()} lists
 * them and {@link #get(String)} reads any field by its API name, so a newer API never breaks
 * parsing.
 */
public abstract class TypizerObject {
    private static final Set<String> REDACTED = new HashSet<>(Arrays.asList("output", "fields", "metadata", "secret"));

    private final Map<String, Object> data;
    private final Set<String> known;

    protected TypizerObject(Map<String, Object> data, Set<String> knownFields) {
        this.data = data == null ? Collections.<String, Object>emptyMap() : Collections.unmodifiableMap(data);
        this.known = knownFields;
    }

    /** Field names as a set — for the {@code FIELDS} constant of each model. */
    protected static Set<String> fields(String... names) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(names)));
    }

    /** The object as the API sent it (read-only, keys in the API's order). */
    public final Map<String, Object> toMap() {
        return data;
    }

    /** The object as compact JSON — e.g. to hand it to Jackson or Gson. */
    public final String toJson() {
        return Json.write(data);
    }

    /** Any field by its API name ({@code "created_at"}), including fields newer than this SDK. */
    public final Object get(String apiFieldName) {
        return data.get(apiFieldName);
    }

    /** Fields the API sent that this version of the SDK has no accessor for. */
    public final Map<String, Object> extra() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : data.entrySet()) {
            if (!known.contains(e.getKey())) out.put(e.getKey(), e.getValue());
        }
        return Collections.unmodifiableMap(out);
    }

    /** The object type ({@code "run"}, {@code "file"}, …). */
    public String object() {
        return str("object");
    }

    @Override
    public final boolean equals(Object o) {
        return o != null && o.getClass() == getClass() && ((TypizerObject) o).data.equals(data);
    }

    @Override
    public final int hashCode() {
        return data.hashCode();
    }

    /**
     * Class name and JSON with document data elided: {@code output}, {@code fields},
     * {@code metadata} and webhook secrets are shown as {@code "…"} — a {@code toString()} that ends
     * up in a log must not carry personal data from documents.
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + Json.write(redact(data));
    }

    private static Object redact(Object v) {
        if (v instanceof Map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                String k = String.valueOf(e.getKey());
                out.put(k, REDACTED.contains(k) && e.getValue() != null ? "…" : redact(e.getValue()));
            }
            return out;
        }
        if (v instanceof List) {
            List<Object> out = new ArrayList<>();
            for (Object item : (List<?>) v) out.add(redact(item));
            return out;
        }
        return v;
    }

    // ---------------------------------------------------------------- typed reads

    protected final String str(String k) {
        Object v = data.get(k);
        return v instanceof String ? (String) v : null;
    }

    protected final Integer integer(String k) {
        Object v = data.get(k);
        return v instanceof Number ? ((Number) v).intValue() : null;
    }

    protected final Long longValue(String k) {
        Object v = data.get(k);
        return v instanceof Number ? ((Number) v).longValue() : null;
    }

    protected final Boolean bool(String k) {
        Object v = data.get(k);
        return v instanceof Boolean ? (Boolean) v : null;
    }

    protected final boolean flag(String k) {
        return Boolean.TRUE.equals(data.get(k));
    }

    protected final OffsetDateTime dateTime(String k) {
        String s = str(k);
        if (s == null) return null;
        try {
            return OffsetDateTime.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    protected final LocalDate date(String k) {
        String s = str(k);
        if (s == null) return null;
        try {
            return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    protected final Map<String, Object> map(String k) {
        Object v = data.get(k);
        return v instanceof Map ? Collections.unmodifiableMap((Map<String, Object>) v) : null;
    }

    @SuppressWarnings("unchecked")
    protected final List<Object> list(String k) {
        Object v = data.get(k);
        return v instanceof List ? Collections.unmodifiableList((List<Object>) v) : Collections.emptyList();
    }

    protected final List<String> strings(String k) {
        Object v = data.get(k);
        if (!(v instanceof List)) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (Object item : (List<?>) v) if (item instanceof String) out.add((String) item);
        return Collections.unmodifiableList(out);
    }

    protected final Map<String, String> stringMap(String k) {
        Object v = data.get(k);
        if (!(v instanceof Map)) return Collections.emptyMap();
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
            if (e.getValue() != null) out.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
        }
        return Collections.unmodifiableMap(out);
    }

    @SuppressWarnings("unchecked")
    protected final <T> T object(String k, Function<Map<String, Object>, T> factory) {
        Object v = data.get(k);
        return v instanceof Map ? factory.apply((Map<String, Object>) v) : null;
    }

    @SuppressWarnings("unchecked")
    protected final <T> List<T> objects(String k, Function<Map<String, Object>, T> factory) {
        Object v = data.get(k);
        if (!(v instanceof List)) return Collections.emptyList();
        List<T> out = new ArrayList<>();
        for (Object item : (List<?>) v) if (item instanceof Map) out.add(factory.apply((Map<String, Object>) item));
        return Collections.unmodifiableList(out);
    }

    protected final <E extends Enum<E> & TypizerEnum> E enumValue(String k, Class<E> type) {
        return TypizerEnum.parse(type, str(k));
    }
}
