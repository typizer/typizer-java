package ru.typizer.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Where a field's value was found in the document. */
public final class FieldSource extends TypizerObject {
    static final Set<String> FIELDS = fields("page", "bbox", "text");

    public FieldSource(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** Page number, from 1. */
    public Integer page() {
        return integer("page");
    }

    /**
     * {@code [x0, y0, x1, y1]} as fractions of the page from its top-left corner, or {@code null}
     * when the position is not known.
     */
    public List<Double> bbox() {
        Object v = get("bbox");
        if (!(v instanceof List)) return null;
        List<Double> out = new ArrayList<>();
        for (Object n : (List<?>) v) {
            if (!(n instanceof Number)) return null;
            out.add(((Number) n).doubleValue());
        }
        return Collections.unmodifiableList(out);
    }

    /** The text in the document the value comes from. */
    public String text() {
        return str("text");
    }

    @Override
    public String toString() {
        return "FieldSource{page=" + page() + ", bbox=" + bbox() + "}";
    }
}
