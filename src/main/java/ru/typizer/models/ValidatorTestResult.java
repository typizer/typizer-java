package ru.typizer.models;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** The answer to {@code validators().test(…)}: a verdict per value, in the order sent. */
public final class ValidatorTestResult extends TypizerObject {
    static final Set<String> FIELDS = fields("object", "explain", "results");

    public ValidatorTestResult(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** What the check does, in Russian. */
    public String explain() {
        return str("explain");
    }

    /** One result per value, in the order of {@code values}. */
    public List<Result> results() {
        return objects("results", Result::new);
    }

    @Override
    public String toString() {
        // Never the values: they may come from documents.
        int passed = 0;
        List<Result> all = results();
        for (Result r : all) if (r.pass()) passed++;
        return "ValidatorTestResult{results=" + all.size() + ", passed=" + passed + "}";
    }

    /** The verdict on one value. */
    public static final class Result extends TypizerObject {
        static final Set<String> FIELDS = fields("value", "pass", "reason", "message", "rule_index");

        public Result(Map<String, Object> data) {
            super(data, FIELDS);
        }

        /** The value as sent: a {@code String} or a number. */
        public Object value() {
            return get("value");
        }

        /** The value passes the check. */
        public boolean pass() {
            return flag("pass");
        }

        /** Why it fails ({@code pattern_mismatch}, {@code checksum_mismatch}, …); {@code null} when it passes. */
        public String reason() {
            return str("reason");
        }

        /** The warning text a run would show for it; {@code null} when it passes. */
        public String message() {
            return str("message");
        }

        /** The index of the rule that failed (from 0); {@code null} when it passes. */
        public Integer ruleIndex() {
            return integer("rule_index");
        }

        @Override
        public String toString() {
            return "Result{pass=" + pass() + (reason() != null ? ", reason=" + reason() : "") + "}";
        }
    }
}
