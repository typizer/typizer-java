package ru.typizer;

import java.util.concurrent.CancellationException;

import ru.typizer.http.HttpRequest;

/**
 * Cancellation of one asynchronous call ({@link TypizerAsyncClient}): {@code future.cancel(true)}
 * aborts the running HTTP attempt, the pause between retries and the wait for a run, and no further
 * requests are sent. Bound to the worker thread while the call runs.
 */
final class CallContext {
    private static final ThreadLocal<CallContext> CURRENT = new ThreadLocal<>();

    private boolean cancelled;
    private Thread thread;
    private HttpRequest inFlight;

    static CallContext current() {
        return CURRENT.get();
    }

    /** Throws {@link CancellationException} if the call of the current thread was cancelled. */
    static void checkCancelled() {
        CallContext c = CURRENT.get();
        if (c != null && c.isCancelled()) throw new CancellationException("The Typizer call was cancelled");
    }

    synchronized boolean isCancelled() {
        return cancelled;
    }

    synchronized void start() {
        thread = Thread.currentThread();
        CURRENT.set(this);
    }

    /** Ends the call on its thread; after this no interrupt reaches the (pooled) thread. */
    void finish() {
        synchronized (this) {
            thread = null;
            inFlight = null;
        }
        CURRENT.remove();
        Thread.interrupted(); // clear an interrupt delivered by a cancel that raced with the end
    }

    synchronized void cancel() {
        if (cancelled) return;
        cancelled = true;
        if (inFlight != null) inFlight.cancel();
        if (thread != null) thread.interrupt();
    }

    /** The attempt in flight, so a cancel can abort it; returns false if already cancelled. */
    synchronized boolean attach(HttpRequest request) {
        if (cancelled) {
            request.cancel();
            return false;
        }
        inFlight = request;
        return true;
    }

    synchronized void detach() {
        inFlight = null;
    }
}
