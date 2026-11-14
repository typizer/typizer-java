package ru.typizer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import ru.typizer.errors.ApiException;
import ru.typizer.errors.ConnectionException;
import ru.typizer.errors.TimeoutException;
import ru.typizer.errors.TypizerException;
import ru.typizer.http.HttpRequest;
import ru.typizer.http.HttpResponse;
import ru.typizer.http.HttpTransport;
import ru.typizer.http.RequestBody;
import ru.typizer.internal.Json;

/**
 * What every call goes through: URL, headers, {@code Idempotency-Key}, timeout per attempt, retries
 * with {@code Retry-After} or exponential backoff, error classes, logging. Shared by a client and its
 * {@code withOptions} copies (same transport and connection pool).
 */
final class Core {
    /** Times out attempts: one daemon thread for every client in the JVM. */
    private static final ScheduledThreadPoolExecutor TIMER;

    static {
        TIMER = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "typizer-timeouts");
            t.setDaemon(true);
            return t;
        });
        TIMER.setRemoveOnCancelPolicy(true);
    }

    /** {@link TypizerClient#withRawResponse}: the last response of the calls on this thread. */
    static final ThreadLocal<Object[]> CAPTURE = new ThreadLocal<>();

    final String apiKey;
    final String baseUrl;
    final Duration timeout;
    final Duration connectTimeout;
    final int maxRetries;
    final Duration maxRetryDelay;
    final HttpTransport transport;
    final Map<String, String> defaultHeaders;
    final String userAgent;
    final TypizerLogger logger;
    private final Set<String> maintenanceSeen;

    Core(String apiKey, String baseUrl, Duration timeout, Duration connectTimeout, int maxRetries, Duration maxRetryDelay,
            HttpTransport transport, Map<String, String> defaultHeaders, String userAgent, TypizerLogger logger,
            Set<String> maintenanceSeen) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.timeout = timeout;
        this.connectTimeout = connectTimeout;
        this.maxRetries = maxRetries;
        this.maxRetryDelay = maxRetryDelay;
        this.transport = transport;
        this.defaultHeaders = defaultHeaders;
        this.userAgent = userAgent;
        this.logger = logger;
        this.maintenanceSeen = maintenanceSeen != null ? maintenanceSeen : ConcurrentHashMap.<String>newKeySet();
    }

    Core withSettings(Duration timeout, Integer maxRetries) {
        return new Core(apiKey, baseUrl, timeout != null ? timeout : this.timeout, connectTimeout,
                maxRetries != null ? maxRetries : this.maxRetries, maxRetryDelay, transport, defaultHeaders, userAgent,
                logger, maintenanceSeen);
    }

    // ---------------------------------------------------------------- one call

    /** One logical call: operation, path parameters, query, body and its limits. */
    static final class Call {
        final Op op;
        final String[] pathParams;
        final Map<String, Object> query = new LinkedHashMap<>();
        RequestBody body;
        RequestOptions options = RequestOptions.NONE;
        /** The attempt timeout is at least this (server-side {@code wait} + 15 s). */
        Duration minTimeout;
        /** {@code System.nanoTime()} after which no retry pause may end ({@code extract}'s overall deadline); 0 — none. */
        long deadline;
        /** Sent as is, instead of a generated key ({@code extract} keeps one key for the whole call). */
        String idempotencyKey;

        Call(Op op, String... pathParams) {
            this.op = op;
            this.pathParams = pathParams;
        }

        Call query(String name, Object value) {
            if (value != null) query.put(name, value);
            return this;
        }

        Call body(RequestBody body) {
            this.body = body;
            return this;
        }

        Call jsonBody(Map<String, Object> body) {
            this.body = RequestBody.json(Json.write(body));
            return this;
        }

        Call options(RequestOptions options) {
            this.options = options != null ? options : RequestOptions.NONE;
            return this;
        }
    }

    /** The response of a call: status, headers and the whole body (JSON calls). */
    static final class Result {
        final int status;
        final Map<String, List<String>> headers;
        final byte[] body;
        final HttpResponse open; // downloads: the unread response

        Result(int status, Map<String, List<String>> headers, byte[] body, HttpResponse open) {
            this.status = status;
            this.headers = headers;
            this.body = body;
            this.open = open;
        }

        Map<String, Object> json() {
            String text = new String(body, StandardCharsets.UTF_8);
            try {
                return Json.parseObject(text);
            } catch (IllegalArgumentException e) {
                // A 2xx that is not JSON: a captive proxy, an HTML page. Report it like an error body.
                throw ApiException.from(status, headers, text);
            }
        }
    }

    /** A JSON call, parsed into a model. */
    <T> T json(Call call, Function<Map<String, Object>, T> model) {
        Result r = execute(call, false);
        return model.apply(r.json());
    }

    Map<String, Object> json(Call call) {
        return execute(call, false).json();
    }

    /** A call whose response is a file. */
    Download download(Call call) {
        return new Download(execute(call, true).open);
    }

    Result execute(Call call, boolean stream) {
        Op op = call.op;
        URI uri = uri(call);
        RequestOptions opts = call.options;
        int retries = opts.maxRetries() != null ? opts.maxRetries() : maxRetries;
        int attempts = 1 + retries;
        Duration attemptTimeout = opts.timeout() != null ? opts.timeout() : timeout;
        if (call.minTimeout != null && call.minTimeout.compareTo(attemptTimeout) > 0) attemptTimeout = call.minTimeout;

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        headers.put("User-Agent", userAgent);
        for (Map.Entry<String, String> h : defaultHeaders.entrySet()) putHeader(headers, h.getKey(), h.getValue());
        for (Map.Entry<String, String> h : opts.extraHeaders().entrySet()) putHeader(headers, h.getKey(), h.getValue());
        headers.put("Authorization", "Bearer " + apiKey);
        if (op.sendsIdempotencyKey()) {
            String key = call.idempotencyKey != null ? call.idempotencyKey : opts.idempotencyKey();
            // One key per logical call, generated before the first attempt and kept for every retry.
            headers.put("Idempotency-Key", key != null ? key : UUID.randomUUID().toString());
        }

        String path = uri.getRawPath();
        for (int attempt = 1; ; attempt++) {
            CallContext.checkCancelled();
            HttpRequest request = new HttpRequest(op.method, uri, headers, call.body, connectTimeout, attemptTimeout);
            CallContext ctx = CallContext.current();
            if (ctx != null && !ctx.attach(request)) throw new CancellationException("The Typizer call was cancelled");
            AtomicBoolean timedOut = new AtomicBoolean();
            ScheduledFuture<?> watchdog = TIMER.schedule(() -> {
                timedOut.set(true);
                request.cancel();
            }, attemptTimeout.toMillis(), TimeUnit.MILLISECONDS);
            long started = System.nanoTime();
            HttpResponse response = null;
            TypizerException failure;
            Duration retryAfter = null;
            try {
                response = transport.execute(request);
                int status = response.statusCode();
                Map<String, List<String>> respHeaders = response.headers();
                noteMaintenance(response);
                if (status < 400 && stream) {
                    watchdog.cancel(false);
                    logAttempt(op, path, status, response.header("X-Request-Id"), attempt, attempts, started, null);
                    capture(status, respHeaders);
                    HttpResponse open = response;
                    response = null; // the caller reads and closes it
                    return new Result(status, respHeaders, null, open);
                }
                byte[] body = readAll(response.body());
                watchdog.cancel(false);
                logAttempt(op, path, status, response.header("X-Request-Id"), attempt, attempts, started, null);
                if (status < 400) {
                    capture(status, respHeaders);
                    return new Result(status, respHeaders, body, null);
                }
                ApiException error = ApiException.from(status, respHeaders, new String(body, StandardCharsets.UTF_8));
                if (!error.isRetryable() || attempt >= attempts) throw error;
                retryAfter = error.retryAfter();
                if (retryAfter != null && retryAfter.compareTo(maxRetryDelay) > 0) throw error;
                failure = error;
            } catch (IOException e) {
                watchdog.cancel(false);
                if (ctx != null && ctx.isCancelled()) throw cancelled(e);
                boolean timeoutHit = timedOut.get() || e instanceof SocketTimeoutException;
                logAttempt(op, path, 0, null, attempt, attempts, started, e);
                TypizerException error = timeoutHit
                        ? new TimeoutException("Request timed out after " + seconds(attemptTimeout) + " (" + op.method
                                + " " + path + ")", e)
                        : new ConnectionException("Connection error: " + describe(e) + " (" + op.method + " " + path
                                + ")", e);
                boolean notSent = e instanceof ConnectException || e instanceof UnknownHostException
                        || e instanceof NoRouteToHostException;
                // PATCH is not idempotent and has no key: retry it only if it certainly did not leave.
                boolean mayRetry = !"PATCH".equals(op.method) || notSent;
                if (!mayRetry || attempt >= attempts) throw error;
                failure = error;
            } finally {
                watchdog.cancel(false);
                if (ctx != null) ctx.detach();
                if (response != null) closeQuietly(response);
            }

            Duration pause = retryAfter != null ? retryAfter : backoff(attempt - 1);
            if (call.deadline != 0 && System.nanoTime() + pause.toNanos() > call.deadline) throw failure;
            if (logger.isEnabled(TypizerLogger.Level.INFO)) {
                String why = failure instanceof ApiException
                        ? ((ApiException) failure).statusCode() + " " + ((ApiException) failure).code()
                        : failure instanceof TimeoutException ? "timeout" : "connection error";
                String rid = failure instanceof ApiException ? ((ApiException) failure).requestId() : null;
                logger.log(TypizerLogger.Level.INFO, "typizer: retrying " + op.method + " " + path + " in "
                        + seconds(pause) + " after " + why + " (attempt " + (attempt + 1) + "/" + attempts
                        + (rid != null ? ", request_id=" + rid : "") + ")");
            }
            sleep(pause);
        }
    }

    private static void putHeader(Map<String, String> headers, String name, String value) {
        if (name == null || value == null || "Authorization".equalsIgnoreCase(name)) return;
        for (String existing : new ArrayList<>(headers.keySet())) {
            if (existing.equalsIgnoreCase(name)) headers.remove(existing);
        }
        headers.put(name, value);
    }

    /** {@code delay = min(8 s, 0.5 s × 2^n) × random(0.75, 1.0)}, {@code n} from 0 (SPEC §7.2). */
    static Duration backoff(int n) {
        double seconds = Math.min(8.0, 0.5 * Math.pow(2, n)) * (0.75 + 0.25 * ThreadLocalRandom.current().nextDouble());
        return Duration.ofMillis((long) (seconds * 1000));
    }

    /** Sleeps; a cancel or an interrupt ends the call with {@link CancellationException}. */
    static void sleep(Duration d) {
        CallContext.checkCancelled();
        if (d.isZero() || d.isNegative()) return;
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw cancelled(e);
        }
        CallContext.checkCancelled();
    }

    private static CancellationException cancelled(Throwable cause) {
        CancellationException c = new CancellationException("The Typizer call was cancelled");
        c.initCause(cause);
        return c;
    }

    private void capture(int status, Map<String, List<String>> headers) {
        Object[] slot = CAPTURE.get();
        if (slot != null) {
            slot[0] = status;
            slot[1] = headers;
        }
    }

    private void noteMaintenance(HttpResponse response) {
        String m = response.header("Typizer-Maintenance");
        if (m == null) return;
        String id = m.split(";", 2)[0].trim();
        if (maintenanceSeen.add(id) && logger.isEnabled(TypizerLogger.Level.WARN)) {
            logger.log(TypizerLogger.Level.WARN, "typizer: maintenance announced: " + m.trim()
                    + " (details: client.status(), GET /v1/status)");
        }
    }

    private void logAttempt(Op op, String path, int status, String requestId, int attempt, int attempts, long started,
            IOException error) {
        if (!logger.isEnabled(TypizerLogger.Level.DEBUG)) return;
        Duration took = Duration.ofNanos(System.nanoTime() - started);
        String outcome = error != null ? error.getClass().getSimpleName() : String.valueOf(status);
        logger.log(TypizerLogger.Level.DEBUG, "typizer: " + op.method + " " + path + " -> " + outcome + " in "
                + seconds(took) + " (request_id=" + (requestId != null ? requestId : "-") + ", attempt " + attempt + "/"
                + attempts + ")");
    }

    static String seconds(Duration d) {
        return String.format(Locale.ROOT, "%.2fs", d.toMillis() / 1000.0);
    }

    private static String describe(IOException e) {
        String m = e.getMessage();
        return e.getClass().getSimpleName() + (m != null ? ": " + m : "");
    }

    private static byte[] readAll(InputStream in) throws IOException {
        if (in == null) return new byte[0];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        for (int n; (n = in.read(buf)) != -1; ) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private static void closeQuietly(HttpResponse r) {
        try {
            r.close();
        } catch (IOException ignored) {
            // nothing to do
        }
    }

    // ---------------------------------------------------------------- URLs

    URI uri(Call call) {
        String template = call.op.path;
        StringBuilder path = new StringBuilder();
        int param = 0;
        for (int i = 0; i < template.length(); i++) {
            char c = template.charAt(i);
            if (c == '{') {
                int end = template.indexOf('}', i);
                if (param >= call.pathParams.length || call.pathParams[param] == null || call.pathParams[param].isEmpty())
                    throw new IllegalArgumentException(template.substring(i + 1, end) + " must not be empty");
                path.append(encode(call.pathParams[param++]));
                i = end;
            } else {
                path.append(c);
            }
        }
        StringBuilder url = new StringBuilder(baseUrl).append(path);
        char sep = '?';
        for (Map.Entry<String, Object> q : call.query.entrySet()) {
            if (q.getValue() == null) continue; // not set by the user: not sent (SPEC §3)
            url.append(sep).append(encode(q.getKey())).append('=').append(encode(queryValue(q.getValue())));
            sep = '&';
        }
        return URI.create(url.toString());
    }

    private static String queryValue(Object v) {
        if (v instanceof OffsetDateTime) return ((OffsetDateTime) v).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        if (v instanceof Instant || v instanceof LocalDate) return v.toString();
        if (v instanceof ru.typizer.models.TypizerEnum) return ((ru.typizer.models.TypizerEnum) v).value();
        return String.valueOf(v);
    }

    /** Percent-encodes everything but RFC 3986 unreserved characters. */
    static String encode(String s) {
        StringBuilder sb = new StringBuilder();
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xff;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '.'
                    || c == '_' || c == '~') {
                sb.append((char) c);
            } else {
                sb.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16)))
                        .append(Character.toUpperCase(Character.forDigit(c & 15, 16)));
            }
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- helpers for resources

    /** The 413 the API would answer, raised before sending a file over the limit. */
    static ApiException fileTooLarge(long size) {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "https://docs.typizer.ru/errors/file_too_large");
        problem.put("title", "File too large");
        problem.put("status", 413);
        problem.put("detail", "The file is " + size + " bytes; the limit is " + TypizerFile.MAX_BYTES
                + " bytes (50 MiB). Not sent.");
        problem.put("code", "file_too_large");
        problem.put("doc_url", "https://docs.typizer.ru/errors/file_too_large");
        return ApiException.from(413, Collections.<String, List<String>>emptyMap(), Json.write(problem));
    }

    /** Checks the size of an upload before anything is sent. */
    static void checkSize(TypizerFile file) {
        long size;
        try {
            size = file.size();
        } catch (UncheckedIOException e) {
            if (e.getCause() instanceof TypizerFile.FileTooLarge) throw fileTooLarge(TypizerFile.MAX_BYTES + 1);
            throw e;
        }
        if (size > TypizerFile.MAX_BYTES) throw fileTooLarge(size);
    }

    /** The client's {@code toString}: never the key itself. */
    static String maskKey(String key) {
        if (key == null) return null;
        int prefixEnd = key.startsWith("tz_live_") ? 8 : key.startsWith("tz_test_") ? 8 : Math.min(3, key.length());
        String tail = key.length() > prefixEnd + 4 ? key.substring(key.length() - 4) : "";
        return key.substring(0, prefixEnd) + "…" + tail;
    }
}
