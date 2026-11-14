package ru.typizer.models;

import java.util.Map;
import java.util.Set;

/** The organization of the API key. */
public final class Organization extends TypizerObject {
    static final Set<String> FIELDS = fields("object", "id", "name", "settings");

    public Organization(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** {@code id}. */
    public String id() {
        return str("id");
    }

    /** {@code name}. */
    public String name() {
        return str("name");
    }

    /** {@code settings}. */
    public Map<String, Object> settings() {
        return map("settings");
    }
}
