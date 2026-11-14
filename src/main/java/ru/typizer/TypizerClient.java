package ru.typizer;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import javax.net.ssl.SSLContext;

import ru.typizer.errors.ConfigurationException;
import ru.typizer.errors.RunFailedException;
import ru.typizer.errors.WaitTimeoutException;
import ru.typizer.http.HttpTransport;
import ru.typizer.http.UrlConnectionTransport;
import ru.typizer.models.Balance;
import ru.typizer.models.Me;
import ru.typizer.models.Organization;
import ru.typizer.models.Run;
import ru.typizer.models.Status;
import ru.typizer.models.Usage;

/**
 * The Typizer API client: documents in, JSON by your schema out.
 *
 * <pre>{@code
 * TypizerClient client = TypizerClient.builder().apiKey(System.getenv("TYPIZER_API_KEY")).build();
 * Run run = client.extract(Paths.get("invoice.pdf"), schema);
 * Map<String, Object> data = run.output();
 * }</pre>
 *
 * <p>One client per application: it is thread-safe and reuses connections. Defaults: base URL
 * {@code https://api.typizer.ru} ({@code TYPIZER_BASE_URL}), 75 s per HTTP attempt, 10 s to connect,
 * 2 retries for network errors, 408, 429 and 5xx (with {@code Retry-After} or exponential backoff),
 * an {@code Idempotency-Key} on every {@code POST}.
 */
public final class TypizerClient implements AutoCloseable {
    /** Version of this SDK. */
    public static final String VERSION = "0.1.0";
    /** {@code https://api.typizer.ru}. */
    public static final String DEFAULT_BASE_URL = "https://api.typizer.ru";

    private final Core core;
    private final boolean ownsTransport;
    private final ExecutorService ownedExecutor;
    private final ExecutorService executor;
    private final RunService runs;
    private final FileService files;
    private final TypizerService typizers;
    private final CatalogService catalog;
    private final BatchService batches;
    private final WebhookEndpointService webhookEndpoints;
    private volatile TypizerAsyncClient async;

    private TypizerClient(Core core, boolean ownsTransport, ExecutorService executor, ExecutorService ownedExecutor) {
        this.core = core;
        this.ownsTransport = ownsTransport;
        this.executor = executor;
        this.ownedExecutor = ownedExecutor;
        this.runs = new RunService(core);
        this.files = new FileService(core);
        this.typizers = new TypizerService(core);
        this.catalog = new CatalogService(core);
        this.batches = new BatchService(core);
        this.webhookEndpoints = new WebhookEndpointService(core);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** A client configured from {@code TYPIZER_API_KEY} (and {@code TYPIZER_BASE_URL}, if set). */
    public static TypizerClient fromEnv() {
        return builder().build();
    }

    // ---------------------------------------------------------------- resources

    public RunService runs() {
        return runs;
    }

    public FileService files() {
        return files;
    }

    public TypizerService typizers() {
        return typizers;
    }

    public CatalogService catalog() {
        return catalog;
    }

    public BatchService batches() {
        return batches;
    }

    public WebhookEndpointService webhookEndpoints() {
        return webhookEndpoints;
    }

    // ---------------------------------------------------------------- extract

    /**
     * Recognizes a document and waits for the result (up to 300 s): creates the run with server-side
     * waiting, polls if needed, returns the finished run.
     *
     * @param schema a JSON Schema — {@code Map} (use {@code LinkedHashMap}: order of properties is
     *     the order of columns), JSON text, or a Jackson/Gson tree node
     * @throws RunFailedException if the run failed (code and run inside)
     * @throws WaitTimeoutException if it did not finish in time (it goes on and will be billed)
     */
    public Run extract(Path file, Object schema) {
        return extract(TypizerFile.fromPath(file), schema);
    }

    /** A document from {@code java.io.File}. */
    public Run extract(java.io.File file, Object schema) {
        return extract(TypizerFile.fromFile(file), schema);
    }

    /** A document in memory, named {@code document}; use {@link TypizerFile#fromBytes(byte[], String)} to name it. */
    public Run extract(byte[] file, Object schema) {
        return extract(TypizerFile.fromBytes(file), schema);
    }

    /** A document from a stream (read into memory, at most 50 MiB; not closed). */
    public Run extract(InputStream file, Object schema) {
        return extract(TypizerFile.fromStream(file), schema);
    }

    /** A document at a public URL. */
    public Run extract(URL url, Object schema) {
        return extract(TypizerFile.fromUrl(url), schema);
    }

    /** Any document: {@code TypizerFile.fromPath/fromBytes/fromStream/fromUrl/fromId}. */
    public Run extract(TypizerFile file, Object schema) {
        return extract(ExtractParams.builder().file(file).schema(schema).build(), RequestOptions.NONE);
    }

    /** All parameters: catalog schema or typizer instead of a schema, metadata, format, timeout… */
    public Run extract(ExtractParams params) {
        return extract(params, RequestOptions.NONE);
    }

    /** With an {@code Idempotency-Key} of yours (kept for the whole call, retries included) and other per-call settings. */
    public Run extract(ExtractParams params, RequestOptions options) {
        return runs.extract(params, options);
    }

    // ---------------------------------------------------------------- account

    /** {@code GET /v1/me}: the organization, the key and its scopes. */
    public Me me() {
        return me(RequestOptions.NONE);
    }

    public Me me(RequestOptions options) {
        return core.json(new Core.Call(Op.getMe).options(options), Me::new);
    }

    /** {@code GET /v1/balance}: pages available. */
    public Balance balance() {
        return balance(RequestOptions.NONE);
    }

    public Balance balance(RequestOptions options) {
        return core.json(new Core.Call(Op.getBalance).options(options), Balance::new);
    }

    /** {@code GET /v1/usage}: runs and billed pages for the current month, by day. */
    public Usage usage() {
        return usage(null, RequestOptions.NONE);
    }

    public Usage usage(UsageParams params) {
        return usage(params, RequestOptions.NONE);
    }

    public Usage usage(UsageParams params, RequestOptions options) {
        Core.Call call = new Core.Call(Op.getUsage).options(options);
        if (params != null) call.query.putAll(params.query);
        return core.json(call, Usage::new);
    }

    /** {@code GET /v1/organization}. */
    public Organization organization() {
        return organization(RequestOptions.NONE);
    }

    public Organization organization(RequestOptions options) {
        return core.json(new Core.Call(Op.getOrganization).options(options), Organization::new);
    }

    /** {@code GET /v1/status}: planned maintenance and incidents (no key needed). */
    public Status status() {
        return status(RequestOptions.NONE);
    }

    public Status status(RequestOptions options) {
        return core.json(new Core.Call(Op.getStatus).options(options), Status::new);
    }

    /** {@code GET /v1/openapi.json}: the public OpenAPI document. */
    public Map<String, Object> openApiDocument() {
        return Collections.unmodifiableMap(core.json(new Core.Call(Op.getOpenApiDocument)));
    }

    // ---------------------------------------------------------------- variants

    /**
     * Runs {@code call} on this client and returns its value with the status and headers of the
     * last HTTP response it made ({@code X-Request-Id}, {@code RateLimit-*},
     * {@code Idempotent-Replayed}, {@code Location}):
     * {@code client.withRawResponse(c -> c.runs().get(id))}.
     */
    public <T> TypizerResponse<T> withRawResponse(Function<TypizerClient, T> call) {
        Object[] previous = Core.CAPTURE.get();
        Object[] slot = new Object[2];
        Core.CAPTURE.set(slot);
        try {
            T value = call.apply(this);
            if (slot[0] == null) throw new IllegalStateException("The call made no request to the API");
            @SuppressWarnings("unchecked")
            Map<String, List<String>> headers = (Map<String, List<String>>) slot[1];
            return new TypizerResponse<>(value, (Integer) slot[0], headers);
        } finally {
            if (previous != null) Core.CAPTURE.set(previous);
            else Core.CAPTURE.remove();
        }
    }

    /** A copy with another timeout per HTTP attempt; shares connections with this client. */
    public TypizerClient withTimeout(Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive");
        return new TypizerClient(core.withSettings(timeout, null), false, executor, null);
    }

    /** A copy with another number of retries; shares connections with this client. */
    public TypizerClient withMaxRetries(int maxRetries) {
        if (maxRetries < 0) throw new IllegalArgumentException("maxRetries must be >= 0");
        return new TypizerClient(core.withSettings(null, maxRetries), false, executor, null);
    }

    /** The same API with {@code CompletableFuture}s; {@code future.cancel(true)} aborts the call. */
    public TypizerAsyncClient async() {
        TypizerAsyncClient a = async;
        if (a == null) {
            synchronized (this) {
                if (async == null) async = new TypizerAsyncClient(this, executor);
                a = async;
            }
        }
        return a;
    }

    /** Releases what the client created itself: the default transport and the async thread pool. */
    @Override
    public void close() {
        if (ownsTransport) core.transport.close();
        if (ownedExecutor != null) ownedExecutor.shutdown();
    }

    /** Settings, with the API key masked ({@code tz_live_…a1b2}). */
    @Override
    public String toString() {
        return "TypizerClient{baseUrl=" + core.baseUrl + ", apiKey=" + Core.maskKey(core.apiKey) + ", timeout="
                + core.timeout + ", maxRetries=" + core.maxRetries + "}";
    }

    String userAgent() {
        return core.userAgent;
    }

    // ---------------------------------------------------------------- builder

    /** Settings of a client; everything but the key has a default. */
    public static final class Builder {
        private String apiKey;
        private String baseUrl;
        private Duration timeout = Duration.ofSeconds(75);
        private Duration connectTimeout = Duration.ofSeconds(10);
        private int maxRetries = 2;
        private Duration maxRetryDelay = Duration.ofSeconds(60);
        private HttpTransport transport;
        private String proxyUrl;
        private Proxy proxy;
        private SSLContext sslContext;
        private final Map<String, String> defaultHeaders = new LinkedHashMap<>();
        private String appName;
        private String appVersion;
        private TypizerLogger logger;
        private ExecutorService executor;

        private Builder() {}

        /** The API key ({@code tz_live_…}); without it, {@code TYPIZER_API_KEY}. */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /** {@code https://api.typizer.ru} by default ({@code TYPIZER_BASE_URL}); e.g. a dedicated installation. */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /** Timeout of one HTTP attempt (connect, send, response), 75 s by default. */
        public Builder timeout(Duration timeout) {
            if (timeout == null || timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive");
            this.timeout = timeout;
            return this;
        }

        /** Timeout of connecting, 10 s by default. */
        public Builder connectTimeout(Duration connectTimeout) {
            if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero())
                throw new IllegalArgumentException("connectTimeout must be positive");
            this.connectTimeout = connectTimeout;
            return this;
        }

        /** Automatic retries, 2 by default (up to 3 attempts); 0 turns them off. */
        public Builder maxRetries(int maxRetries) {
            if (maxRetries < 0) throw new IllegalArgumentException("maxRetries must be >= 0");
            this.maxRetries = maxRetries;
            return this;
        }

        /** The longest {@code Retry-After} the client waits for, 60 s by default; a longer one fails at once. */
        public Builder maxRetryDelay(Duration maxRetryDelay) {
            this.maxRetryDelay = maxRetryDelay;
            return this;
        }

        /**
         * Your own transport (OkHttp, Apache HttpClient, a GOST TLS stack…); then proxy and TLS are
         * set up there. The client does not close it.
         */
        public Builder httpClient(HttpTransport transport) {
            this.transport = transport;
            return this;
        }

        /**
         * A proxy URL: {@code http://host:3128}, {@code http://user:password@host:3128} or
         * {@code socks5://host:1080}. Without it the JVM's proxy settings apply
         * ({@code https.proxyHost}, {@code -Djava.net.useSystemProxies=true}).
         */
        public Builder proxy(String proxyUrl) {
            this.proxyUrl = proxyUrl;
            return this;
        }

        /** A {@link Proxy} ({@link Proxy#NO_PROXY} — always direct). */
        public Builder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        /** TLS settings for the default transport: a corporate root CA, a client certificate, GOST TLS. */
        public Builder sslContext(SSLContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        /** A header for every request ({@code Authorization} cannot be overridden). */
        public Builder defaultHeader(String name, String value) {
            this.defaultHeaders.put(name, value);
            return this;
        }

        /** Your application, appended to {@code User-Agent}: {@code acme-erp/2.1.0}. */
        public Builder appInfo(String name, String version) {
            this.appName = name;
            this.appVersion = version;
            return this;
        }

        /** Where log lines go; {@link TypizerLogger#jul()} by default (silent below WARNING). */
        public Builder logger(TypizerLogger logger) {
            this.logger = logger;
            return this;
        }

        /** Threads for {@link TypizerClient#async()}; by default the client's own daemon pool. */
        public Builder executor(ExecutorService executor) {
            this.executor = executor;
            return this;
        }

        /** @throws ConfigurationException without a usable API key, base URL or proxy */
        public TypizerClient build() {
            String key = apiKey != null ? apiKey : System.getenv("TYPIZER_API_KEY");
            if (key == null || key.trim().isEmpty()) {
                throw new ConfigurationException(
                        "No API key: pass TypizerClient.builder().apiKey(…) or set TYPIZER_API_KEY (create one at my.typizer.ru)");
            }
            for (int i = 0; i < key.length(); i++) {
                if (Character.isWhitespace(key.charAt(i)) || Character.isISOControl(key.charAt(i)))
                    throw new ConfigurationException("The API key contains whitespace — check how it was copied");
            }
            String url = baseUrl;
            if (url == null || url.trim().isEmpty()) url = System.getenv("TYPIZER_BASE_URL");
            if (url == null || url.trim().isEmpty()) url = DEFAULT_BASE_URL;
            url = normalizeBaseUrl(url.trim());

            boolean owns = transport == null;
            HttpTransport t = transport;
            if (t == null) {
                UrlConnectionTransport.Builder tb = UrlConnectionTransport.builder();
                if (proxy != null) tb.proxy(proxy);
                if (proxyUrl != null) configureProxy(tb, proxyUrl);
                if (sslContext != null) tb.sslContext(sslContext);
                t = tb.build();
            } else if (proxy != null || proxyUrl != null || sslContext != null) {
                throw new ConfigurationException("proxy and sslContext configure the default transport; set them on your own httpClient instead");
            }

            Map<String, String> headers = new LinkedHashMap<>(defaultHeaders);
            String ua = userAgent(appName, appVersion);
            for (Map.Entry<String, String> h : headers.entrySet()) {
                if ("User-Agent".equalsIgnoreCase(h.getKey())) ua = h.getValue();
            }
            TypizerLogger log = logger != null ? logger : TypizerLogger.fromEnvironment();
            Core core = new Core(key, url, timeout, connectTimeout, maxRetries,
                    maxRetryDelay != null ? maxRetryDelay : Duration.ofSeconds(60), t, Collections.unmodifiableMap(headers),
                    ua, log, null);
            ExecutorService owned = executor == null ? newDaemonPool() : null;
            return new TypizerClient(core, owns, executor != null ? executor : owned, owned);
        }

        private static String normalizeBaseUrl(String url) {
            while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
            if (url.endsWith("/v1")) url = url.substring(0, url.length() - 3);
            URI u;
            try {
                u = new URI(url);
            } catch (URISyntaxException e) {
                throw new ConfigurationException("Bad base URL: " + url);
            }
            if (!"https".equalsIgnoreCase(u.getScheme()) && !"http".equalsIgnoreCase(u.getScheme()) || u.getHost() == null)
                throw new ConfigurationException("The base URL must be http(s)://host[:port]: " + url);
            return url;
        }

        private static void configureProxy(UrlConnectionTransport.Builder tb, String proxyUrl) {
            URI u;
            try {
                u = new URI(proxyUrl.trim());
            } catch (URISyntaxException e) {
                throw new ConfigurationException("Bad proxy URL (expected http://[user:password@]host:port)");
            }
            String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
            if (u.getHost() == null) throw new ConfigurationException("Bad proxy URL (expected http://[user:password@]host:port)");
            Proxy.Type type;
            int defaultPort;
            if (scheme.equals("http")) {
                type = Proxy.Type.HTTP;
                defaultPort = 80;
            } else if (scheme.equals("socks5") || scheme.equals("socks")) {
                type = Proxy.Type.SOCKS;
                defaultPort = 1080;
            } else {
                throw new ConfigurationException("Proxy scheme must be http or socks5");
            }
            tb.proxy(new Proxy(type, InetSocketAddress.createUnresolved(u.getHost(), u.getPort() > 0 ? u.getPort() : defaultPort)));
            String userInfo = u.getRawUserInfo();
            if (userInfo != null) {
                int colon = userInfo.indexOf(':');
                String user = colon >= 0 ? userInfo.substring(0, colon) : userInfo;
                String password = colon >= 0 ? userInfo.substring(colon + 1) : "";
                tb.proxyCredentials(decode(user), decode(password));
            }
        }

        private static String decode(String s) {
            try {
                return URLDecoder.decode(s.replace("+", "%2B"), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public String toString() {
            return "TypizerClient.Builder{baseUrl=" + baseUrl + ", apiKey=" + (apiKey == null ? null : Core.maskKey(apiKey)) + "}";
        }
    }

    /** {@code typizer-java/<ver> (java/<ver>; <os>/<arch>)[ <app>/<ver>]} (SPEC §2.3). */
    static String userAgent(String appName, String appVersion) {
        StringBuilder sb = new StringBuilder("typizer-java/").append(VERSION);
        String javaVersion = System.getProperty("java.version");
        String os = osName(System.getProperty("os.name"));
        String arch = System.getProperty("os.arch");
        if (javaVersion != null && os != null && arch != null) {
            sb.append(" (java/").append(javaVersion).append("; ").append(os).append('/').append(arch).append(')');
        }
        if (appName != null && !appName.isEmpty()) {
            sb.append(' ').append(appName.replace(' ', '-'));
            if (appVersion != null && !appVersion.isEmpty()) sb.append('/').append(appVersion);
        }
        return sb.toString();
    }

    private static String osName(String name) {
        if (name == null) return null;
        String n = name.toLowerCase(Locale.ROOT);
        if (n.startsWith("mac") || n.contains("darwin")) return "darwin";
        if (n.startsWith("windows")) return "windows";
        if (n.startsWith("linux")) return "linux";
        return n.replace(' ', '-');
    }

    private static ExecutorService newDaemonPool() {
        AtomicInteger n = new AtomicInteger();
        return Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "typizer-async-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }
}
