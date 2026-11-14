package ru.typizer.http;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Authenticator;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.PasswordAuthentication;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

/**
 * The default transport: {@link HttpURLConnection} from the JDK — no dependencies, works on Java 8,
 * pools keep-alive connections JVM-wide. Proxy: an explicit {@link Proxy}, otherwise the JVM's
 * {@link ProxySelector} (system properties {@code https.proxyHost}/{@code https.proxyPort},
 * {@code -Djava.net.useSystemProxies=true}). TLS: the JVM's trust store, or the {@link SSLContext}
 * you pass (a corporate root certificate, GOST TLS through a JCA/JSSE provider such as КриптоПро JCP).
 *
 * <p>{@code HttpURLConnection} cannot send {@code PATCH}: on Java 11+ those requests go through
 * {@code java.net.http.HttpClient} with the same proxy and TLS settings; on Java 8–10 the method is set
 * by reflection.
 */
public final class UrlConnectionTransport implements HttpTransport {
    private final Proxy proxy;
    private final String proxyUser;
    private final String proxyPassword;
    private final SSLContext sslContext;
    private volatile Object jdkHttpClient; // java.net.http.HttpClient for PATCH on Java 11+

    private UrlConnectionTransport(Builder b) {
        this.proxy = b.proxy;
        this.proxyUser = b.proxyUser;
        this.proxyPassword = b.proxyPassword;
        this.sslContext = b.sslContext;
    }

    /** A transport with the JVM defaults. */
    public UrlConnectionTransport() {
        this(new Builder());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Proxy proxy;
        private String proxyUser;
        private String proxyPassword;
        private SSLContext sslContext;

        private Builder() {}

        /** An explicit proxy ({@link Proxy#NO_PROXY} — go direct, ignoring the JVM settings). */
        public Builder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        /** An HTTP proxy at {@code host:port}. */
        public Builder proxy(String host, int port) {
            return proxy(new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port)));
        }

        /** Proxy login and password ({@code Proxy-Authorization: Basic}). */
        public Builder proxyCredentials(String user, String password) {
            this.proxyUser = user;
            this.proxyPassword = password;
            return this;
        }

        /** TLS settings: your trust store, client certificate or a GOST TLS provider's context. */
        public Builder sslContext(SSLContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        public UrlConnectionTransport build() {
            return new UrlConnectionTransport(this);
        }
    }

    @Override
    public HttpResponse execute(HttpRequest request) throws IOException {
        if ("PATCH".equals(request.method()) && JdkHttpClient.AVAILABLE) return executeWithHttpClient(request);

        URL url = request.uri().toURL();
        HttpURLConnection conn = (HttpURLConnection) (proxy != null ? url.openConnection(proxy) : url.openConnection());
        request.onCancel(conn::disconnect);
        conn.setConnectTimeout(millis(request.connectTimeout()));
        conn.setReadTimeout(millis(request.timeout()));
        conn.setInstanceFollowRedirects(false);
        conn.setUseCaches(false);
        if (conn instanceof HttpsURLConnection && sslContext != null) {
            ((HttpsURLConnection) conn).setSSLSocketFactory(sslContext.getSocketFactory());
        }
        setMethod(conn, request.method());
        for (Map.Entry<String, String> h : request.headers().entrySet()) conn.setRequestProperty(h.getKey(), h.getValue());
        if (proxyUser != null) applyProxyCredentials(conn, url);
        RequestBody body = request.body();
        if (body != null) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", body.contentType());
            // Fixed length: no chunking, and HttpURLConnection does not silently resend the POST.
            conn.setFixedLengthStreamingMode(body.contentLength());
        }

        try {
            conn.connect();
        } catch (UnknownHostException | ConnectException | NoRouteToHostException e) {
            throw e;
        } catch (SocketTimeoutException e) {
            throw notSent("connect timed out", e);
        } catch (IOException e) {
            if (request.isCancelled()) throw e;
            throw notSent(e.getMessage(), e);
        }

        if (body != null) {
            try (OutputStream out = conn.getOutputStream()) {
                body.writeTo(out);
            }
        }
        int status = conn.getResponseCode();
        InputStream in = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (in == null) in = new ByteArrayInputStream(new byte[0]);
        return new HttpResponse(status, conn.getHeaderFields(), in, null);
    }

    private static ConnectException notSent(String message, Throwable cause) {
        ConnectException e = new ConnectException(message);
        e.initCause(cause);
        return e;
    }

    private static int millis(Duration d) {
        if (d == null) return 0;
        long ms = d.toMillis();
        return ms <= 0 ? 1 : (int) Math.min(Integer.MAX_VALUE, ms);
    }

    private static String basic(String user, String password) {
        String token = user + ":" + (password == null ? "" : password);
        return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    /** {@code setRequestMethod}, plus the reflection fallback for PATCH on Java 8–10. */
    private static void setMethod(HttpURLConnection conn, String method) throws ProtocolException {
        try {
            conn.setRequestMethod(method);
        } catch (ProtocolException e) {
            try {
                Field f = HttpURLConnection.class.getDeclaredField("method");
                f.setAccessible(true);
                f.set(conn, method);
                try {
                    // HTTPS: the request is made by the delegate connection inside.
                    Field d = conn.getClass().getDeclaredField("delegate");
                    d.setAccessible(true);
                    Object delegate = d.get(conn);
                    if (delegate instanceof HttpURLConnection) f.set(delegate, method);
                } catch (NoSuchFieldException noDelegate) {
                    // plain HTTP
                }
            } catch (ReflectiveOperationException | RuntimeException reflection) {
                ProtocolException pe = new ProtocolException("HTTP method " + method
                        + " is not supported by HttpURLConnection on this JVM; pass your own HttpTransport");
                pe.initCause(reflection);
                throw pe;
            }
        }
    }

    // ---------------------------------------------------------------- PATCH on Java 11+

    private HttpResponse executeWithHttpClient(HttpRequest request) throws IOException {
        try {
            Object client = jdkHttpClient;
            if (client == null) {
                synchronized (this) {
                    client = jdkHttpClient;
                    if (client == null) jdkHttpClient = client = JdkHttpClient.newClient(proxy, proxyUser, proxyPassword, sslContext, request.connectTimeout());
                }
            }
            return JdkHttpClient.send(client, request);
        } catch (ReflectiveOperationException e) {
            throw new IOException("java.net.http.HttpClient failed: " + e, e);
        }
    }

    /** {@code java.net.http} through reflection: this class is compiled for Java 8. */
    private static final class JdkHttpClient {
        static final boolean AVAILABLE = available();

        private static boolean available() {
            try {
                Class.forName("java.net.http.HttpClient");
                return true;
            } catch (ClassNotFoundException | LinkageError e) {
                return false;
            }
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        static Object newClient(Proxy proxy, String user, String password, SSLContext ssl, Duration connectTimeout)
                throws ReflectiveOperationException {
            Class<?> hc = Class.forName("java.net.http.HttpClient");
            Class<?> b = Class.forName("java.net.http.HttpClient$Builder");
            Object builder = hc.getMethod("newBuilder").invoke(null);
            // HTTP/1.1 like HttpURLConnection: no h2c upgrade attempts on plain-HTTP endpoints.
            Class<Enum> version = (Class<Enum>) Class.forName("java.net.http.HttpClient$Version");
            b.getMethod("version", version).invoke(builder, Enum.valueOf(version, "HTTP_1_1"));
            if (connectTimeout != null) b.getMethod("connectTimeout", Duration.class).invoke(builder, connectTimeout);
            ProxySelector selector;
            if (proxy == null) selector = ProxySelector.getDefault();
            else if (proxy.type() == Proxy.Type.HTTP && proxy.address() instanceof InetSocketAddress)
                selector = (ProxySelector) ProxySelector.class.getMethod("of", InetSocketAddress.class).invoke(null, proxy.address());
            else selector = null; // NO_PROXY
            if (selector != null) b.getMethod("proxy", ProxySelector.class).invoke(builder, selector);
            if (ssl != null) b.getMethod("sslContext", SSLContext.class).invoke(builder, ssl);
            if (user != null) b.getMethod("authenticator", Authenticator.class).invoke(builder, new ProxyAuth(user, password));
            return b.getMethod("build").invoke(builder);
        }

        static HttpResponse send(Object client, HttpRequest request) throws IOException, ReflectiveOperationException {
            Class<?> hc = Class.forName("java.net.http.HttpClient");
            Class<?> r = Class.forName("java.net.http.HttpRequest");
            Class<?> rb = Class.forName("java.net.http.HttpRequest$Builder");
            Class<?> pub = Class.forName("java.net.http.HttpRequest$BodyPublisher");
            Class<?> pubs = Class.forName("java.net.http.HttpRequest$BodyPublishers");
            Class<?> handler = Class.forName("java.net.http.HttpResponse$BodyHandler");
            Class<?> handlers = Class.forName("java.net.http.HttpResponse$BodyHandlers");
            Class<?> resp = Class.forName("java.net.http.HttpResponse");
            Class<?> hh = Class.forName("java.net.http.HttpHeaders");

            Object builder = r.getMethod("newBuilder", URI.class).invoke(null, request.uri());
            byte[] bytes = new byte[0];
            if (request.body() != null) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                request.body().writeTo(buf);
                bytes = buf.toByteArray();
                rb.getMethod("header", String.class, String.class).invoke(builder, "Content-Type", request.body().contentType());
            }
            Object publisher = pubs.getMethod("ofByteArray", byte[].class).invoke(null, (Object) bytes);
            rb.getMethod("method", String.class, pub).invoke(builder, request.method(), publisher);
            for (Map.Entry<String, String> h : request.headers().entrySet()) {
                rb.getMethod("header", String.class, String.class).invoke(builder, h.getKey(), h.getValue());
            }
            if (request.timeout() != null) rb.getMethod("timeout", Duration.class).invoke(builder, request.timeout());
            Object req = rb.getMethod("build").invoke(builder);
            Object bodyHandler = handlers.getMethod("ofInputStream").invoke(null);
            CompletableFuture<?> future = (CompletableFuture<?>) hc.getMethod("sendAsync", r, handler).invoke(client, req, bodyHandler);
            request.onCancel(() -> future.cancel(true));
            Object response;
            try {
                response = future.get();
            } catch (InterruptedException e) {
                future.cancel(true);
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", e);
            } catch (java.util.concurrent.CancellationException e) {
                throw new IOException("cancelled", e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause != null && cause.getClass().getName().equals("java.net.http.HttpConnectTimeoutException"))
                    throw notSent("connect timed out", cause);
                if (cause instanceof ConnectException) throw (ConnectException) cause;
                if (cause != null && cause.getClass().getName().equals("java.net.http.HttpTimeoutException"))
                    throw (SocketTimeoutException) new SocketTimeoutException("request timed out").initCause(cause);
                if (cause instanceof IOException) throw (IOException) cause;
                throw new IOException(String.valueOf(cause), cause);
            }
            int status = (Integer) resp.getMethod("statusCode").invoke(response);
            @SuppressWarnings("unchecked")
            Map<String, List<String>> headers = (Map<String, List<String>>) hh.getMethod("map").invoke(resp.getMethod("headers").invoke(response));
            InputStream in = (InputStream) resp.getMethod("body").invoke(response);
            return new HttpResponse(status, headers, in, null);
        }
    }

    /**
     * Proxy login without ever handing it to the target server. A {@code Proxy-Authorization} request
     * header is only safe for plain HTTP through an explicit HTTP proxy (the proxy sees the request
     * line and strips it). For HTTPS the header would travel inside the tunnel to the API itself,
     * and with a SOCKS or JVM-selected proxy it may go straight to the target — so there only the
     * per-connection authenticator (Java 9+) is used, and Java 8 fails loudly instead of leaking.
     */
    private void applyProxyCredentials(HttpURLConnection conn, URL url) throws IOException {
        boolean explicitHttpProxy = proxy != null && proxy.type() == Proxy.Type.HTTP;
        if (explicitHttpProxy && "http".equals(url.getProtocol())) {
            conn.setRequestProperty("Proxy-Authorization", basic(proxyUser, proxyPassword));
            return;
        }
        if (ProxyAuth.setAuthenticator(conn, proxyUser, proxyPassword)) return;
        throw new IOException("Typizer: proxy credentials for HTTPS or a SOCKS/system proxy need Java 9+ "
                + "(per-connection authenticator); on Java 8 configure Authenticator.setDefault "
                + "or pass your own HttpTransport");
    }

    /** Proxy login for one connection, never the JVM-wide {@link Authenticator#setDefault}. */
    private static final class ProxyAuth extends Authenticator {
        private final String user;
        private final String password;

        ProxyAuth(String user, String password) {
            this.user = user;
            this.password = password == null ? "" : password;
        }

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            return getRequestorType() == RequestorType.PROXY ? new PasswordAuthentication(user, password.toCharArray()) : null;
        }

        /** {@code HttpURLConnection.setAuthenticator} (Java 9+); false on Java 8. */
        static boolean setAuthenticator(HttpURLConnection conn, String user, String password) {
            try {
                Method m = HttpURLConnection.class.getMethod("setAuthenticator", Authenticator.class);
                m.invoke(conn, new ProxyAuth(user, password));
                return true;
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                return false;
            }
        }
    }

    @Override
    public String toString() {
        return "UrlConnectionTransport{proxy=" + (proxy == null ? "jvm-default" : proxy.type() + " " + proxy.address())
                + (proxyUser != null ? ", proxyCredentials=***" : "") + (sslContext != null ? ", sslContext=" + sslContext.getProtocol() : "") + "}";
    }
}
