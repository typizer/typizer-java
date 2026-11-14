package ru.typizer.http;

import java.io.IOException;

/**
 * Sends one HTTP request. The client does everything else on top: retries, idempotency keys,
 * timeouts per attempt, error classes, logging. Replace the default {@link UrlConnectionTransport}
 * to send requests through OkHttp, Apache HttpClient, a JAX-RS client or a GOST TLS stack — pass it
 * to {@code TypizerClient.builder().httpClient(…)}.
 *
 * <p>Contract:
 * <ul>
 *   <li>return any response the server gave, including 4xx/5xx — do not throw on a status;
 *   <li>throw {@link IOException} if there is no response; throw {@link java.net.ConnectException}
 *       (or {@link java.net.UnknownHostException}) when the request certainly was not sent — only
 *       then may the client retry a {@code PATCH};
 *   <li>honor {@link HttpRequest#connectTimeout()} and {@link HttpRequest#timeout()} if the HTTP
 *       library can, and register an abort hook with {@link HttpRequest#onCancel(Runnable)} if it
 *       can abort a running call — the client calls it when the attempt times out or the call is
 *       cancelled;
 *   <li>be safe for concurrent use.
 * </ul>
 * The client closes a transport it created itself, never one passed in by the application.
 */
public interface HttpTransport extends AutoCloseable {
    /** Sends the request and returns the response (the caller closes it). */
    HttpResponse execute(HttpRequest request) throws IOException;

    /** Releases connections. The default does nothing. */
    @Override
    default void close() {}
}
