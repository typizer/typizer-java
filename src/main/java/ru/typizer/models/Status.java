package ru.typizer.models;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Service status: planned maintenance and incidents. */
public final class Status extends TypizerObject {
    static final Set<String> FIELDS = fields("object", "data");

    public Status(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** {@code data}. */
    public List<MaintenanceEvent> data() {
        return objects("data", MaintenanceEvent::new);
    }
}
