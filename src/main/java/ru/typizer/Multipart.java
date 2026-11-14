package ru.typizer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import ru.typizer.http.RequestBody;

/** {@code multipart/form-data} (RFC 7578) with a known length; files are streamed from disk. */
final class Multipart implements RequestBody {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String boundary;
    private final List<Object[]> parts = new ArrayList<>(); // {headerBytes, TypizerFile or byte[] value}

    Multipart() {
        StringBuilder b = new StringBuilder("----typizer-");
        for (int i = 0; i < 24; i++) b.append(Character.forDigit(RANDOM.nextInt(36), 36));
        this.boundary = b.toString();
    }

    Multipart field(String name, String value) {
        String head = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + escape(name) + "\"\r\n\r\n";
        parts.add(new Object[] {head.getBytes(StandardCharsets.UTF_8), value.getBytes(StandardCharsets.UTF_8)});
        return this;
    }

    Multipart file(String name, TypizerFile file) {
        String head = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + escape(name) + "\"; filename=\""
                + escape(file.filename()) + "\"\r\nContent-Type: " + file.contentType() + "\r\n\r\n";
        parts.add(new Object[] {head.getBytes(StandardCharsets.UTF_8), file});
        return this;
    }

    /** Quotes and line breaks cannot appear inside a quoted header parameter (as browsers do). */
    private static String escape(String s) {
        return s.replace("\"", "%22").replace("\r", "%0D").replace("\n", "%0A");
    }

    @Override
    public String contentType() {
        return "multipart/form-data; boundary=" + boundary;
    }

    @Override
    public long contentLength() {
        long n = 0;
        for (Object[] p : parts) {
            n += ((byte[]) p[0]).length + 2; // value + CRLF
            n += p[1] instanceof byte[] ? ((byte[]) p[1]).length : ((TypizerFile) p[1]).size();
        }
        return n + ("--" + boundary + "--\r\n").length();
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        byte[] crlf = {'\r', '\n'};
        for (Object[] p : parts) {
            out.write((byte[]) p[0]);
            if (p[1] instanceof byte[]) {
                out.write((byte[]) p[1]);
            } else {
                TypizerFile f = (TypizerFile) p[1];
                if (f.kind() == TypizerFile.Kind.PATH) {
                    try (InputStream in = Files.newInputStream(f.path())) {
                        byte[] buf = new byte[64 * 1024];
                        for (int n; (n = in.read(buf)) != -1; ) out.write(buf, 0, n);
                    }
                } else {
                    out.write(f.content());
                }
            }
            out.write(crlf);
        }
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
    }
}
