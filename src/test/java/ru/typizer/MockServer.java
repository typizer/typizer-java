package ru.typizer;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.typizer.internal.Json;

/**
 * The SDK conformance mock (packages/sdk-spec/conformance): {@code TYPIZER_MOCK_URL} if set
 * (scripts/test.sh runs it in a container next to Maven), otherwise {@code node server.js} started
 * here on a free port — from the monorepo, or the copy the public mirror keeps in src/test/mock.
 */
final class MockServer {
    private static final Pattern LISTENING = Pattern.compile("mock on (http://\\S+)");
    private static final AtomicInteger KEYS = new AtomicInteger();
    private static String baseUrl;
    private static Process process;

    private MockServer() {}

    static synchronized String baseUrl() {
        if (baseUrl != null) return baseUrl;
        String env = System.getenv("TYPIZER_MOCK_URL");
        if (env != null && !env.isEmpty()) {
            baseUrl = env.replaceAll("/+$", "");
            waitHealthy();
            return baseUrl;
        }
        Path server = find("packages/sdk-spec/conformance/server.js", "../sdk-spec/conformance/server.js",
                "src/test/mock/server.js");
        String node = which("node");
        if (server == null || node == null) {
            throw new IllegalStateException("The conformance mock is not available: set TYPIZER_MOCK_URL, or install"
                    + " Node 20+ (server: " + server + ", node: " + node + ")");
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(node, server.toString()).redirectErrorStream(true);
            pb.environment().put("PORT", "0");
            pb.environment().put("HOST", "127.0.0.1");
            pb.environment().put("MOCK_SLOW_RESPONSE_MS", "3000");
            process = pb.start();
            BufferedReader out = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = out.readLine()) != null) {
                Matcher m = LISTENING.matcher(line);
                if (m.find()) {
                    baseUrl = m.group(1);
                    break;
                }
            }
            if (baseUrl == null) throw new IllegalStateException("The mock exited without listening");
            Thread drain = new Thread(() -> {
                try {
                    while (out.readLine() != null) {
                        // keep the pipe empty
                    }
                } catch (IOException ignored) {
                    // process ended
                }
            }, "mock-output");
            drain.setDaemon(true);
            drain.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> process.destroy()));
            return baseUrl;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start the mock", e);
        }
    }

    private static void waitHealthy() {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (true) {
            try {
                if (get("/_mock/health").contains("true")) return;
            } catch (RuntimeException e) {
                if (System.nanoTime() > until) throw e;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** A file of the monorepo (from the package or the repo root) or of the mirror. */
    static Path find(String... candidates) {
        for (String c : candidates) {
            for (String base : new String[] {"../..", "."}) {
                Path p = Paths.get(base, c).normalize();
                if (p.toFile().isFile()) return p.toAbsolutePath();
            }
        }
        return null;
    }

    private static String which(String binary) {
        String path = System.getenv("PATH");
        if (path == null) return null;
        for (String dir : path.split(File.pathSeparator)) {
            File f = new File(dir, binary);
            if (f.canExecute()) return f.getAbsolutePath();
        }
        return null;
    }

    /** A fresh key for a scenario, so tests never share mock state. */
    static String key(String scenario) {
        return "tz_test_" + scenario + "__java" + KEYS.incrementAndGet() + "_" + Long.toHexString(System.nanoTime());
    }

    /** A client for a scenario, with short default retry pauses where the scenario allows. */
    static TypizerClient client(String key) {
        return TypizerClient.builder().apiKey(key).baseUrl(baseUrl()).build();
    }

    /** The mock's request log for one key, in order. */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> requests(String key) {
        Map<String, Object> json = Json.parseObject(get("/_mock/requests?key=" + Core.encode(key)));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : (List<Object>) json.get("data")) out.add((Map<String, Object>) o);
        return out;
    }

    static List<Map<String, Object>> requests(String key, String operationId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : requests(key)) if (operationId.equals(r.get("operation_id"))) out.add(r);
        return out;
    }

    static String get(String path) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(baseUrl() + path).openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(10000);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] b = new byte[8192];
                for (int n; (n = in.read(b)) != -1; ) buf.write(b, 0, n);
                return new String(buf.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Mock " + path + ": " + e, e);
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object o) {
        return (Map<String, Object>) o;
    }

    static String header(Map<String, Object> request, String name) {
        Object v = map(request.get("headers")).get(name);
        return v == null ? null : v.toString();
    }
}
