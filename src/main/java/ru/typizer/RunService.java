package ru.typizer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import ru.typizer.errors.RunFailedException;
import ru.typizer.errors.WaitTimeoutException;
import ru.typizer.internal.Json;
import ru.typizer.models.Format;
import ru.typizer.models.Run;

/** Runs: {@code client.runs()}. */
public final class RunService {
    static final Duration DEFAULT_WAIT_TIMEOUT = Duration.ofSeconds(300);
    static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(1);
    private static final Duration MAX_POLL_INTERVAL = Duration.ofSeconds(5);
    private static final int MAX_SERVER_WAIT = 60;

    private final Core core;

    RunService(Core core) {
        this.core = core;
    }

    /**
     * Creates a run and returns at once ({@code queued}); see {@link #create(ExtractParams, Duration)}
     * to let the API hold the request until the run finishes. {@code POST /v1/extract}, or
     * {@code POST /v1/typizers/{id}/runs} for a typizer.
     */
    public Run create(ExtractParams params) {
        return create(params, null, RequestOptions.NONE);
    }

    /**
     * Creates a run; the API holds the request up to {@code wait} (0–60 s) until the run finishes,
     * otherwise returns it {@code queued}/{@code processing}.
     */
    public Run create(ExtractParams params, Duration wait) {
        return create(params, wait, RequestOptions.NONE);
    }

    public Run create(ExtractParams params, Duration wait, RequestOptions options) {
        Integer waitSeconds = null;
        if (wait != null) {
            long s = wait.getSeconds();
            if (s < 0 || s > MAX_SERVER_WAIT) throw new IllegalArgumentException("wait must be 0–60 seconds");
            waitSeconds = (int) s;
        }
        return core.json(createCall(params, waitSeconds, options), Run::new);
    }

    /** {@code extract}/{@code createRun} for {@code params}: JSON, or multipart when the document is local. */
    Core.Call createCall(ExtractParams p, Integer waitSeconds, RequestOptions options) {
        Core.Call call = p.typizerId != null ? new Core.Call(Op.createRun, p.typizerId) : new Core.Call(Op.extract);
        call.options(options);
        if (waitSeconds != null) {
            call.query("wait", waitSeconds);
            call.minTimeout = Duration.ofSeconds(waitSeconds + 15L);
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        if (p.typizerId == null) {
            if (p.schema != null) fields.put("schema", p.schema);
            if (p.catalog != null) fields.put("catalog", p.catalog);
            if (p.instructions != null) fields.put("instructions", p.instructions);
        }
        if (p.mode != null) fields.put("mode", p.mode);
        if (p.format != null) fields.put("format", p.format);
        if (p.metadata != null) fields.put("metadata", p.metadata);
        if (p.webhookUrl != null) fields.put("webhook_url", p.webhookUrl);

        if (p.file.isUpload()) {
            Core.checkSize(p.file);
            Multipart form = new Multipart();
            for (Map.Entry<String, Object> f : fields.entrySet()) {
                Object v = f.getValue();
                form.field(f.getKey(), v instanceof String ? (String) v : Json.write(v)); // schema, metadata: JSON text
            }
            form.file("file", p.file);
            call.body(form);
        } else {
            Map<String, Object> body = new LinkedHashMap<>();
            if (p.file.kind() == TypizerFile.Kind.URL) body.put("url", p.file.value());
            else body.put("file_id", p.file.value());
            body.putAll(fields);
            call.jsonBody(body);
        }
        return call;
    }

    /**
     * The helper behind {@code client.extract}: create with server-side wait, poll until the run is
     * finished (SPEC §4.4), throw {@link RunFailedException} if it failed.
     */
    Run extract(ExtractParams p, RequestOptions options) {
        RequestOptions opts = options != null ? options : RequestOptions.NONE;
        Duration total = p.timeout != null ? p.timeout : DEFAULT_WAIT_TIMEOUT;
        long start = System.nanoTime();
        long deadline = start + total.toNanos();
        String key = opts.idempotencyKey() != null ? opts.idempotencyKey() : UUID.randomUUID().toString();
        long waitSeconds = Math.max(0, Math.min(MAX_SERVER_WAIT, (deadline - System.nanoTime()) / 1_000_000_000L));
        Core.Call call = createCall(p, (int) waitSeconds, opts);
        call.idempotencyKey = key;
        call.deadline = deadline;
        Run run = core.json(call, Run::new);
        run = poll(run, start, deadline, p.pollInterval, opts);
        if (run.status() == ru.typizer.models.RunStatus.FAILED) throw new RunFailedException(run);
        return run;
    }

    private Run poll(Run run, long start, long deadline, Duration pollInterval, RequestOptions opts) {
        Duration interval = pollInterval != null ? pollInterval : DEFAULT_POLL_INTERVAL;
        while (!run.isTerminal()) {
            long now = System.nanoTime();
            if (now >= deadline) throw new WaitTimeoutException(run, Duration.ofNanos(now - start));
            Core.sleep(Duration.ofNanos(Math.min(interval.toNanos(), deadline - now)));
            interval = Duration.ofMillis(Math.min((long) (interval.toMillis() * 1.5), MAX_POLL_INTERVAL.toMillis()));
            Core.Call get = new Core.Call(Op.getRun, run.id()).options(opts);
            get.deadline = deadline;
            run = core.json(get, Run::new);
        }
        return run;
    }

    /** {@code GET /v1/runs/{id}}. */
    public Run get(String id) {
        return get(id, RequestOptions.NONE);
    }

    public Run get(String id, RequestOptions options) {
        return core.json(new Core.Call(Op.getRun, id).options(options), Run::new);
    }

    /**
     * Waits until the run is finished (up to 300 s) and returns it — {@code succeeded} or
     * {@code failed}, without throwing for a failed run.
     *
     * @throws WaitTimeoutException if it is still running (it goes on and will be billed)
     */
    public Run waitFor(String id) {
        return waitFor(id, DEFAULT_WAIT_TIMEOUT, DEFAULT_POLL_INTERVAL);
    }

    public Run waitFor(String id, Duration timeout) {
        return waitFor(id, timeout, DEFAULT_POLL_INTERVAL);
    }

    /** Polls {@code GET /v1/runs/{id}} with pauses from {@code pollInterval} growing ×1.5 up to 5 s. */
    public Run waitFor(String id, Duration timeout, Duration pollInterval) {
        long start = System.nanoTime();
        long deadline = start + (timeout != null ? timeout : DEFAULT_WAIT_TIMEOUT).toNanos();
        Core.Call first = new Core.Call(Op.getRun, id);
        first.deadline = deadline;
        Run run = core.json(first, Run::new);
        return poll(run, start, deadline, pollInterval, RequestOptions.NONE);
    }

    /** A run already in hand: returned as is if finished, otherwise waited for. */
    public Run waitFor(Run run) {
        return run.isTerminal() ? run : waitFor(run.id());
    }

    /** {@code GET /v1/runs}: newest first, without {@code output} and {@code fields}. */
    public Page<Run> list() {
        return list(null, RequestOptions.NONE);
    }

    public Page<Run> list(RunListParams params) {
        return list(params, RequestOptions.NONE);
    }

    public Page<Run> list(RunListParams params, RequestOptions options) {
        Map<String, Object> query = params != null ? params.query : new LinkedHashMap<String, Object>();
        return page(core, Op.listRuns, new String[0], query, "starting_after", options, Run::new);
    }

    /**
     * {@code GET /v1/runs/{id}/output}: the result as a file — {@code json}, {@code xml} (for 1С),
     * {@code csv} or {@code xlsx}; {@code null} format — the run's own.
     */
    public Download output(String id, Format format) {
        return output(id, format == null ? null : ExtractParams.enumValue(format, "format"), RequestOptions.NONE);
    }

    public Download output(String id) {
        return output(id, (String) null, RequestOptions.NONE);
    }

    public Download output(String id, String format, RequestOptions options) {
        return core.download(new Core.Call(Op.getRunOutput, id).query("format", format).options(options));
    }

    /** {@code POST /v1/runs/{id}/retry}: runs a failed run again on the same document. */
    public Run retry(String id) {
        return retry(id, RequestOptions.NONE);
    }

    public Run retry(String id, RequestOptions options) {
        return core.json(new Core.Call(Op.retryRun, id).options(options), Run::new);
    }

    // ---------------------------------------------------------------- shared by the resources

    /** A list call and the {@link Page} over it; the next page repeats the filters with the cursor. */
    @SuppressWarnings("unchecked")
    static <T> Page<T> page(Core core, Op op, String[] pathParams, Map<String, Object> query, String cursorParam,
            RequestOptions options, Function<Map<String, Object>, T> model) {
        Core.Call call = new Core.Call(op, pathParams).options(options);
        call.query.putAll(query);
        Map<String, Object> json = core.json(call);
        List<T> items = new ArrayList<>();
        Object data = json.get("data");
        if (data instanceof List) {
            for (Object item : (List<?>) data) if (item instanceof Map) items.add(model.apply((Map<String, Object>) item));
        }
        boolean hasMore = Boolean.TRUE.equals(json.get("has_more"));
        Object next = json.get("next_cursor");
        String nextCursor = next instanceof String ? (String) next : null;
        return new Page<>(items, hasMore, nextCursor, cursor -> {
            Map<String, Object> q = new LinkedHashMap<>(query);
            q.put(cursorParam, cursor);
            return page(core, op, pathParams, q, cursorParam, options, model);
        });
    }
}
