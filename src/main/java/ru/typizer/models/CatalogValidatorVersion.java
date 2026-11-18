package ru.typizer.models;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** A catalog validator's version: the rule, the tests its author published, the rule in Russian. */
public final class CatalogValidatorVersion extends TypizerObject {
    static final Set<String> FIELDS = fields("ref", "spec", "tests", "tests_hidden", "explain", "rule_preview");

    public CatalogValidatorVersion(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** What to write in a field's {@code x-typizer-check}: {@code inn@1}. */
    public String ref() {
        return str("ref");
    }

    /** The rule as it is. */
    public Map<String, Object> spec() {
        return map("spec");
    }

    /** The published tests. */
    public List<ValidatorTest> tests() {
        return objects("tests", ValidatorTest::new);
    }

    /** How many more of the author's tests pass but are not published. */
    public Integer testsHidden() {
        return integer("tests_hidden");
    }

    /** What is checked, in Russian. */
    public String explain() {
        return str("explain");
    }

    /** The rule as short chips. */
    public List<String> rulePreview() {
        return strings("rule_preview");
    }
}
