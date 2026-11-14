package ru.typizer.models;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pages on the balance of the organization. */
public final class Balance extends TypizerObject {
    static final Set<String> FIELDS = fields("object", "pages_available", "pages_reserved", "lots");

    public Balance(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** {@code pages_available}. */
    public Long pagesAvailable() {
        return longValue("pages_available");
    }

    /** {@code pages_reserved}. */
    public Long pagesReserved() {
        return longValue("pages_reserved");
    }

    /** {@code lots}. */
    public List<BalanceLot> lots() {
        return objects("lots", BalanceLot::new);
    }
}
