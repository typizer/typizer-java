package ru.typizer.internal;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ru.typizer.models.TypizerEnum;
import ru.typizer.models.TypizerObject;

/**
 * A small, strict JSON codec (RFC 8259), so the SDK has no runtime dependencies.
 *
 * <p>Parsing: object → {@link LinkedHashMap} (key order kept — it is the order of the user's schema),
 * array → {@link ArrayList}, string → {@link String}, integer → {@link Long} (or {@link BigInteger}
 * when it does not fit), other numbers → {@link BigDecimal} (exact: amounts of money stay exact),
 * {@code true}/{@code false} → {@link Boolean}, {@code null} → {@code null}.
 *
 * <p>Writing accepts the same types plus any {@link Map}, {@link Collection}, array, {@link Number},
 * SDK models and enums, and {@code java.time} values (ISO 8601). Keys are never sorted.
 */
public final class Json {
    private static final int MAX_DEPTH = 512;

    private Json() {}

    // ---------------------------------------------------------------- parsing

    /** Parses one JSON text. Throws {@link IllegalArgumentException} on malformed input. */
    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWs();
        Object value = p.value(0);
        p.skipWs();
        if (p.pos != text.length()) throw p.error("unexpected trailing characters");
        return value;
    }

    /** Parses a JSON object; anything else is an {@link IllegalArgumentException}. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object v = parse(text);
        if (!(v instanceof Map)) throw new IllegalArgumentException("expected a JSON object");
        return (Map<String, Object>) v;
    }

    private static final class Parser {
        final String s;
        int pos;

        Parser(String s) {
            this.s = s;
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException("Invalid JSON at offset " + pos + ": " + message);
        }

        void skipWs() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos++;
                else break;
            }
        }

        Object value(int depth) {
            if (depth > MAX_DEPTH) throw error("nesting too deep");
            if (pos >= s.length()) throw error("unexpected end of input");
            char c = s.charAt(pos);
            switch (c) {
                case '{':
                    return object(depth);
                case '[':
                    return array(depth);
                case '"':
                    return string();
                case 't':
                    literal("true");
                    return Boolean.TRUE;
                case 'f':
                    literal("false");
                    return Boolean.FALSE;
                case 'n':
                    literal("null");
                    return null;
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) return number();
                    throw error("unexpected character '" + c + "'");
            }
        }

        void literal(String word) {
            if (!s.startsWith(word, pos)) throw error("expected " + word);
            pos += word.length();
        }

        Map<String, Object> object(int depth) {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // {
            skipWs();
            if (pos < s.length() && s.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWs();
                if (pos >= s.length() || s.charAt(pos) != '"') throw error("expected a key");
                String key = string();
                skipWs();
                if (pos >= s.length() || s.charAt(pos) != ':') throw error("expected ':'");
                pos++;
                skipWs();
                map.put(key, value(depth + 1));
                skipWs();
                if (pos >= s.length()) throw error("unterminated object");
                char c = s.charAt(pos++);
                if (c == '}') return map;
                if (c != ',') throw error("expected ',' or '}'");
            }
        }

        List<Object> array(int depth) {
            List<Object> list = new ArrayList<>();
            pos++; // [
            skipWs();
            if (pos < s.length() && s.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (true) {
                skipWs();
                list.add(value(depth + 1));
                skipWs();
                if (pos >= s.length()) throw error("unterminated array");
                char c = s.charAt(pos++);
                if (c == ']') return list;
                if (c != ',') throw error("expected ',' or ']'");
            }
        }

        String string() {
            pos++; // "
            StringBuilder sb = null;
            int start = pos;
            while (true) {
                if (pos >= s.length()) throw error("unterminated string");
                char c = s.charAt(pos);
                if (c == '"') {
                    String out = sb == null ? s.substring(start, pos) : sb.append(s, start, pos).toString();
                    pos++;
                    return out;
                }
                if (c < 0x20) throw error("control character in string");
                if (c != '\\') {
                    pos++;
                    continue;
                }
                if (sb == null) sb = new StringBuilder();
                sb.append(s, start, pos);
                pos++;
                if (pos >= s.length()) throw error("unterminated escape");
                char e = s.charAt(pos++);
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (pos + 4 > s.length()) throw error("bad \\u escape");
                        try {
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw error("bad \\u escape");
                        }
                        pos += 4;
                        break;
                    default:
                        throw error("bad escape \\" + e);
                }
                start = pos;
            }
        }

        Object number() {
            int start = pos;
            if (s.charAt(pos) == '-') pos++;
            if (pos >= s.length()) throw error("bad number");
            if (s.charAt(pos) == '0') {
                pos++;
            } else if (s.charAt(pos) >= '1' && s.charAt(pos) <= '9') {
                while (pos < s.length() && s.charAt(pos) >= '0' && s.charAt(pos) <= '9') pos++;
            } else {
                throw error("bad number");
            }
            boolean integral = true;
            if (pos < s.length() && s.charAt(pos) == '.') {
                integral = false;
                pos++;
                int digits = pos;
                while (pos < s.length() && s.charAt(pos) >= '0' && s.charAt(pos) <= '9') pos++;
                if (pos == digits) throw error("bad number");
            }
            if (pos < s.length() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                integral = false;
                pos++;
                if (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) pos++;
                int digits = pos;
                while (pos < s.length() && s.charAt(pos) >= '0' && s.charAt(pos) <= '9') pos++;
                if (pos == digits) throw error("bad number");
            }
            String text = s.substring(start, pos);
            if (integral) {
                if (text.length() < 19) return Long.parseLong(text);
                BigInteger big = new BigInteger(text);
                return big.bitLength() < 64 ? (Object) big.longValue() : big;
            }
            return new BigDecimal(text);
        }
    }

    // ---------------------------------------------------------------- writing

    /** Serializes a value to compact JSON. */
    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, 0);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v, int depth) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("JSON value nested too deep");
        if (v == null) {
            sb.append("null");
        } else if (v instanceof CharSequence || v instanceof Character) {
            quote(sb, v.toString());
        } else if (v instanceof Boolean) {
            sb.append(v.toString());
        } else if (v instanceof Number) {
            number(sb, (Number) v);
        } else if (v instanceof TypizerObject) {
            write(sb, ((TypizerObject) v).toMap(), depth);
        } else if (v instanceof TypizerEnum) {
            quote(sb, ((TypizerEnum) v).value());
        } else if (v instanceof Enum) {
            quote(sb, ((Enum<?>) v).name());
        } else if (v instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                quote(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue(), depth + 1);
            }
            sb.append('}');
        } else if (v instanceof Iterable) {
            sb.append('[');
            boolean first = true;
            for (Object item : (Iterable<?>) v) {
                if (!first) sb.append(',');
                first = false;
                write(sb, item, depth + 1);
            }
            sb.append(']');
        } else if (v.getClass().isArray()) {
            sb.append('[');
            int n = Array.getLength(v);
            for (int i = 0; i < n; i++) {
                if (i > 0) sb.append(',');
                write(sb, Array.get(v, i), depth + 1);
            }
            sb.append(']');
        } else if (v instanceof OffsetDateTime) {
            quote(sb, ((OffsetDateTime) v).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        } else if (v instanceof Instant || v instanceof LocalDate) {
            quote(sb, v.toString());
        } else if (JsonText.isJsonTreeNode(v)) {
            // Jackson JsonNode, Gson JsonElement, org.json JSONObject: their toString() is JSON.
            sb.append(Json.write(parse(v.toString())));
        } else {
            throw new IllegalArgumentException(
                    "Cannot serialize " + v.getClass().getName() + " to JSON: use Map, List, String, Number or Boolean");
        }
    }

    private static void number(StringBuilder sb, Number n) {
        if (n instanceof Double || n instanceof Float) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d))
                throw new IllegalArgumentException("JSON has no NaN or Infinity");
            if (d == Math.rint(d) && Math.abs(d) < 1e15) sb.append((long) d);
            else sb.append(BigDecimal.valueOf(d).toString());
        } else if (n instanceof BigDecimal) {
            sb.append(((BigDecimal) n).toString());
        } else {
            sb.append(n.toString());
        }
    }

    /** Appends {@code s} as a JSON string literal. */
    public static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20 || c == ' ' || c == ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }
}
