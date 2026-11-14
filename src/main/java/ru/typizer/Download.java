package ru.typizer;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.typizer.http.HttpResponse;

/**
 * A file the API returns ({@code runs().output()}, {@code files().content()},
 * {@code batches().export()}, {@code typizers().xsd()}). The body is streamed: read it once with
 * {@link #saveTo(Path)}, {@link #bytes()}, {@link #text()} or {@link #stream()}, then close (the
 * reading methods close it themselves).
 *
 * <pre>{@code
 * try (Download xml = client.runs().output(run.id(), Format.XML)) {
 *     xml.saveTo(Paths.get("invoice.xml"));
 * }
 * }</pre>
 */
public final class Download implements Closeable {
    private static final Pattern FILENAME_STAR = Pattern.compile("filename\\*\\s*=\\s*(?:UTF-8|utf-8)''([^;]+)");
    private static final Pattern FILENAME = Pattern.compile("filename\\s*=\\s*\"?([^\";]+)\"?");

    private final HttpResponse response;

    Download(HttpResponse response) {
        this.response = response;
    }

    public int statusCode() {
        return response.statusCode();
    }

    /** Response headers (names case-insensitive). */
    public Map<String, List<String>> headers() {
        return response.headers();
    }

    /** {@code Content-Type}: {@code application/xml}, {@code text/csv}, … */
    public String contentType() {
        return response.header("Content-Type");
    }

    /** The file name from {@code Content-Disposition}, or {@code null}. */
    public String filename() {
        String cd = response.header("Content-Disposition");
        if (cd == null) return null;
        Matcher m = FILENAME_STAR.matcher(cd);
        if (m.find()) {
            try {
                return java.net.URLDecoder.decode(m.group(1).trim().replace("+", "%2B"), "UTF-8");
            } catch (java.io.UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
        }
        m = FILENAME.matcher(cd);
        return m.find() ? m.group(1).trim() : null;
    }

    /** The body stream; close the {@code Download} when done. */
    public InputStream stream() {
        return response.body();
    }

    /** The whole body (and closes). */
    public byte[] bytes() {
        try (InputStream in = response.body()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[64 * 1024];
            for (int n; (n = in.read(buf)) != -1; ) out.write(buf, 0, n);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            closeQuietly();
        }
    }

    /** The whole body as text in the charset of {@code Content-Type} (UTF-8 by default), and closes. */
    public String text() {
        Charset cs = StandardCharsets.UTF_8;
        String ct = contentType();
        if (ct != null) {
            Matcher m = Pattern.compile("charset=\"?([\\w.-]+)").matcher(ct.toLowerCase(Locale.ROOT));
            if (m.find() && Charset.isSupported(m.group(1))) cs = Charset.forName(m.group(1));
        }
        return new String(bytes(), cs);
    }

    /** Writes the body to {@code target} (replacing it) and closes; returns {@code target}. */
    public Path saveTo(Path target) {
        try (InputStream in = response.body()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            closeQuietly();
        }
    }

    private void closeQuietly() {
        try {
            response.close();
        } catch (IOException ignored) {
            // already read
        }
    }

    @Override
    public void close() {
        closeQuietly();
    }

    @Override
    public String toString() {
        return "Download{status=" + statusCode() + ", contentType=" + contentType() + "}";
    }
}
