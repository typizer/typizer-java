package ru.typizer.http;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One HTTP attempt, as the client hands it to an {@link HttpTransport}. Headers include
 * {@code Authorization}; a transport must not log them.
 */
public final class HttpRequest {
    private final String method;
    private final URI uri;
    private final Map<String, String> headers;
    private final RequestBody body;
    private final Duration connectTimeout;
    private final Duration timeout;
    private final List<Runnable> cancelHooks = new ArrayList<>();
    private boolean cancelled;

    public HttpRequest(String method, URI uri, Map<String, String> headers, RequestBody body, Duration connectTimeout,
            Duration timeout) {
        this.method = method;
        this.uri = uri;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        this.body = body;
        this.connectTimeout = connectTimeout;
        this.timeout = timeout;
    }

    /** {@code GET}, {@code POST}, {@code PATCH}, {@code PUT} or {@code DELETE}. */
    public String method() {
        return method;
    }

    public URI uri() {
        return uri;
    }

    /** Request headers (without {@code Content-Type} and {@code Content-Length}: see {@link #body()}). */
    public Map<String, String> headers() {
        return headers;
    }

    /** The body, or {@code null}. */
    public RequestBody body() {
        return body;
    }

    public Duration connectTimeout() {
        return connectTimeout;
    }

    /** The whole attempt: connect, send, and receive the response headers and (for JSON) the body. */
    public Duration timeout() {
        return timeout;
    }

    /**
     * Registers a hook that aborts the running call (closes its connection). Runs at once if the
     * attempt is already cancelled.
     */
    public void onCancel(Runnable hook) {
        boolean runNow;
        synchronized (cancelHooks) {
            runNow = cancelled;
            if (!runNow) cancelHooks.add(hook);
        }
        if (runNow) hook.run();
    }

    /** Aborts the attempt: runs the hooks a transport registered. Called by the client. */
    public void cancel() {
        List<Runnable> hooks;
        synchronized (cancelHooks) {
            if (cancelled) return;
            cancelled = true;
            hooks = new ArrayList<>(cancelHooks);
        }
        for (Runnable h : hooks) {
            try {
                h.run();
            } catch (RuntimeException ignored) {
                // Aborting is best effort.
            }
        }
    }

    public boolean isCancelled() {
        synchronized (cancelHooks) {
            return cancelled;
        }
    }

    @Override
    public String toString() {
        return method + " " + uri.getRawPath();
    }
}
