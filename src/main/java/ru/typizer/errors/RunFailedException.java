package ru.typizer.errors;

import ru.typizer.models.Run;

/**
 * {@code extract()}: the run finished with status {@code failed}. Only {@code extract()} throws it —
 * {@code runs().waitFor()} returns a failed run as it is.
 */
public class RunFailedException extends TypizerException {
    private static final long serialVersionUID = 1L;

    private final transient Run run;

    public RunFailedException(Run run) {
        super(message(run));
        this.run = run;
    }

    private static String message(Run run) {
        Run.ErrorInfo e = run.error();
        String code = e != null && e.code() != null ? e.code() : "failed";
        String text = e != null && e.message() != null ? e.message() : "The run failed";
        return "[" + code + "] " + text + " (run " + run.id() + ")";
    }

    /** {@code run.error.code}: {@code document_unreadable}, {@code too_many_pages}, {@code timeout}, … */
    public String code() {
        Run.ErrorInfo e = run.error();
        return e != null ? e.code() : null;
    }

    /** {@code run.error.message}. */
    public String errorMessage() {
        Run.ErrorInfo e = run.error();
        return e != null ? e.message() : null;
    }

    /** The failed run. */
    public Run run() {
        return run;
    }

    /**
     * {@code timeout}, {@code upstream_unavailable} or {@code internal_error}: the same document may
     * succeed on {@code client.runs().retry(run.id())} (not done automatically).
     */
    @Override
    public boolean isRetryable() {
        String c = code();
        return "timeout".equals(c) || "upstream_unavailable".equals(c) || "internal_error".equals(c);
    }
}
