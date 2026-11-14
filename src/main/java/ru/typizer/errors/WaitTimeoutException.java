package ru.typizer.errors;

import java.time.Duration;

import ru.typizer.models.Run;

/**
 * {@code extract()} or {@code runs().waitFor()} stopped waiting before the run finished. The run is
 * not cancelled: it goes on and will be billed. Wait for it again with
 * {@code client.runs().waitFor(e.runId())} or get the result by webhook.
 */
public class WaitTimeoutException extends TypizerException {
    private static final long serialVersionUID = 1L;

    private final transient Run run;
    private final Duration waited;

    public WaitTimeoutException(Run run, Duration waited) {
        super("Run " + run.id() + " is still " + run.statusValue() + " after " + seconds(waited)
                + "; it goes on and will be billed — wait for it with client.runs().waitFor(\"" + run.id()
                + "\") or receive a webhook");
        this.run = run;
        this.waited = waited;
    }

    private static String seconds(Duration d) {
        return String.format(java.util.Locale.ROOT, "%.1f s", d.toMillis() / 1000.0);
    }

    /** The run that is still going on. */
    public String runId() {
        return run.id();
    }

    /** The last known state of the run. */
    public Run run() {
        return run;
    }

    /** How long the SDK waited. */
    public Duration waited() {
        return waited;
    }
}
