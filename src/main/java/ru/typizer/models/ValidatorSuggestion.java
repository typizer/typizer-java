package ru.typizer.models;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A validator drafted from a description and examples ({@code validators().suggest(…)}): a spec to
 * pass to {@code validators().create(…)} as is, how it does on your examples, and existing checks
 * that already fit. Not saved.
 */
public final class ValidatorSuggestion extends TypizerObject {
    static final Set<String> FIELDS = fields("object", "name", "spec", "tests", "converged", "explain",
            "rule_preview", "iterations", "source", "candidates_from_catalog");

    public ValidatorSuggestion(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** A proposed name. */
    public String name() {
        return str("name");
    }

    /** The proposed spec, as a free {@code Map}. */
    public Map<String, Object> spec() {
        return map("spec");
    }

    /** Your examples with what the proposed spec does on each. */
    public List<SuggestedTest> tests() {
        return objects("tests", SuggestedTest::new);
    }

    /** Every example does what it should. */
    public boolean converged() {
        return flag("converged");
    }

    /** What the check does, in Russian. */
    public String explain() {
        return str("explain");
    }

    /** The rules, one line each. */
    public List<String> rulePreview() {
        return strings("rule_preview");
    }

    /** {@code iterations}. */
    public Integer iterations() {
        return integer("iterations");
    }

    /** Where the spec comes from: {@code catalog}, {@code shape} or {@code model} (a newer value is kept as is). */
    public String source() {
        return str("source");
    }

    /** Existing checks (official and yours) that already fit the examples. */
    public List<Candidate> candidatesFromCatalog() {
        return objects("candidates_from_catalog", Candidate::new);
    }

    @Override
    public String toString() {
        // Not the examples: they may come from documents.
        return "ValidatorSuggestion{name=" + name() + ", source=" + source() + ", converged=" + converged() + "}";
    }

    /** An example and what the proposed spec does on it. */
    public static final class SuggestedTest extends TypizerObject {
        static final Set<String> FIELDS = fields("value", "expect", "got");

        public SuggestedTest(Map<String, Object> data) {
            super(data, FIELDS);
        }

        /** The example: a {@code String} or a number. */
        public Object value() {
            return get("value");
        }

        /** {@code pass} or {@code fail}: what it should do. */
        public String expect() {
            return str("expect");
        }

        /** {@code pass} or {@code fail}: what it does. */
        public String got() {
            return str("got");
        }

        @Override
        public String toString() {
            return "SuggestedTest{expect=" + expect() + ", got=" + got() + "}";
        }
    }

    /** An existing check that fits. */
    public static final class Candidate extends TypizerObject {
        static final Set<String> FIELDS = fields("ref", "name", "kind", "explain");

        public Candidate(Map<String, Object> data) {
            super(data, FIELDS);
        }

        /** The reference to put in {@code x-typizer-check}: {@code inn@1}, {@code my/contract-no@1}. */
        public String ref() {
            return str("ref");
        }

        /** {@code name}. */
        public String name() {
            return str("name");
        }

        /** {@code official} or {@code own} (a newer value is kept as is). */
        public String kind() {
            return str("kind");
        }

        /** What the check does, in Russian. */
        public String explain() {
            return str("explain");
        }
    }
}
