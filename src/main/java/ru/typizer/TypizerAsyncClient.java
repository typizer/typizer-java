package ru.typizer;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

import ru.typizer.models.Run;

/**
 * {@code client.async()}: calls on a thread pool, as {@link CompletableFuture}s. Cancelling a future
 * ({@code future.cancel(true)}) aborts the call where it is — the HTTP attempt, the pause before a
 * retry or the wait for a run — and sends nothing more. The run itself, once created, is not
 * cancelled on the server.
 *
 * <pre>{@code
 * CompletableFuture<Run> f = client.async().extract(params);
 * f.thenAccept(run -> save(run.output()));
 * // f.cancel(true);  // e.g. when the user closed the page
 * }</pre>
 */
public final class TypizerAsyncClient {
    private final TypizerClient client;
    private final Executor executor;

    TypizerAsyncClient(TypizerClient client, Executor executor) {
        this.client = client;
        this.executor = executor;
    }

    /** {@link TypizerClient#extract(ExtractParams)} in the background. */
    public CompletableFuture<Run> extract(ExtractParams params) {
        return call(c -> c.extract(params));
    }

    public CompletableFuture<Run> extract(ExtractParams params, RequestOptions options) {
        return call(c -> c.extract(params, options));
    }

    public CompletableFuture<Run> extract(TypizerFile file, Object schema) {
        ExtractParams params = ExtractParams.builder().file(file).schema(schema).build(); // argument errors: now
        return call(c -> c.extract(params));
    }

    /** {@link RunService#waitFor(String, Duration)} in the background. */
    public CompletableFuture<Run> waitFor(String runId, Duration timeout) {
        return call(c -> c.runs().waitFor(runId, timeout));
    }

    /**
     * Any calls of the client in the background, cancellable together:
     * {@code client.async().call(c -> c.runs().list().autoPager().iterator().next())}.
     */
    public <T> CompletableFuture<T> call(Function<TypizerClient, T> work) {
        CallContext ctx = new CallContext();
        CancellableFuture<T> future = new CancellableFuture<>(ctx);
        executor.execute(() -> {
            if (future.isDone()) return;
            ctx.start();
            try {
                future.complete(work.apply(client));
            } catch (Throwable t) {
                future.completeExceptionally(t);
            } finally {
                ctx.finish();
            }
        });
        return future;
    }

    /** Cancelling it cancels the running call. */
    private static final class CancellableFuture<T> extends CompletableFuture<T> {
        private final CallContext ctx;

        CancellableFuture(CallContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean done = super.cancel(mayInterruptIfRunning);
            ctx.cancel();
            return done;
        }
    }
}
