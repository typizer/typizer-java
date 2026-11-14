package ru.typizer.http;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** A response from an {@link HttpTransport}. Closing it releases the connection. */
public final class HttpResponse implements Closeable {
    private final int statusCode;
    private final Map<String, List<String>> headers;
    private final InputStream body;
    private final Closeable onClose;

    /**
     * @param headers response headers; names are made case-insensitive
     * @param body the body stream (empty stream if none)
     * @param onClose released after the body, e.g. a connection; may be null
     */
    public HttpResponse(int statusCode, Map<String, List<String>> headers, InputStream body, Closeable onClose) {
        this.statusCode = statusCode;
        Map<String, List<String>> h = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (headers != null) {
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) h.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
            }
        }
        this.headers = Collections.unmodifiableMap(h);
        this.body = body;
        this.onClose = onClose;
    }

    public int statusCode() {
        return statusCode;
    }

    /** Headers; names are case-insensitive. */
    public Map<String, List<String>> headers() {
        return headers;
    }

    /** The first value of a header, or {@code null}. */
    public String header(String name) {
        List<String> v = headers.get(name);
        return v == null || v.isEmpty() ? null : v.get(0);
    }

    public InputStream body() {
        return body;
    }

    @Override
    public void close() throws IOException {
        try {
            if (body != null) body.close();
        } finally {
            if (onClose != null) onClose.close();
        }
    }
}
