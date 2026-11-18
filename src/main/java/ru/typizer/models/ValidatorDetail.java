package ru.typizer.models;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** A validator with every version (newest first) and the typizers that use it ({@code validators().get(…)}). */
public final class ValidatorDetail extends Validator {
    static final Set<String> FIELDS = fields("object", "id", "ref", "slug", "name", "description", "version",
            "versions_count", "catalog", "forked_from", "created_at", "updated_at", "versions", "used_by");

    public ValidatorDetail(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** Every version, newest first. */
    public List<ValidatorVersion> versions() {
        return objects("versions", ValidatorVersion::new);
    }

    /** Typizers whose schema references this validator. */
    public List<ValidatorUsedBy> usedBy() {
        return objects("used_by", ValidatorUsedBy::new);
    }
}
