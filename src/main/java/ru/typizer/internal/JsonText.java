package ru.typizer.internal;

import java.util.Map;

/** Turns what users pass as a JSON Schema (or any JSON object argument) into a JSON object. */
public final class JsonText {
    private static final String[] TREE_NODE_CLASSES = {
        "com.fasterxml.jackson.databind.JsonNode", // Jackson 2
        "tools.jackson.databind.JsonNode", // Jackson 3
        "com.google.gson.JsonElement", // Gson
        "org.json.JSONObject",
        "javax.json.JsonValue",
        "jakarta.json.JsonValue",
    };

    private JsonText() {}

    /** Whether {@code v} is a JSON tree node of a well-known library, whose toString() is JSON. */
    static boolean isJsonTreeNode(Object v) {
        for (Class<?> c = v.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            if (matches(c)) return true;
            for (Class<?> i : c.getInterfaces()) if (matches(i)) return true;
        }
        return false;
    }

    private static boolean matches(Class<?> c) {
        for (String name : TREE_NODE_CLASSES) if (name.equals(c.getName())) return true;
        return false;
    }

    /**
     * A JSON object as a map, from: a {@link Map}, JSON text, or a Jackson/Gson/org.json/JSON-P
     * tree node. Anything else, or JSON that is not an object, is an {@link IllegalArgumentException}
     * naming {@code what}.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> toObject(Object value, String what) {
        if (value == null) throw new IllegalArgumentException(what + " must not be null");
        if (value instanceof Map) {
            // Round-trip through the writer: validates the content and copies it (key order kept).
            return Json.parseObject(Json.write(value));
        }
        String text;
        if (value instanceof CharSequence) text = value.toString();
        else if (isJsonTreeNode(value)) text = value.toString();
        else
            throw new IllegalArgumentException(what + " must be a Map, JSON text or a JSON tree node, not "
                    + value.getClass().getName());
        Object parsed;
        try {
            parsed = Json.parse(text);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(what + " is not valid JSON (" + e.getMessage() + ")", e);
        }
        if (!(parsed instanceof Map)) throw new IllegalArgumentException(what + " must be a JSON object");
        return (Map<String, Object>) parsed;
    }
}
