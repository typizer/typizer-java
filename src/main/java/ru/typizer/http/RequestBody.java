package ru.typizer.http;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** The body of a request. Every body the client builds can be written again for a retry. */
public interface RequestBody {
    /** {@code Content-Type} of the body, with parameters ({@code multipart/form-data; boundary=…}). */
    String contentType();

    /** Length in bytes. */
    long contentLength();

    /** Writes the whole body; may be called once per attempt. */
    void writeTo(OutputStream out) throws IOException;

    /** A JSON body. */
    static RequestBody json(String json) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        return new RequestBody() {
            @Override
            public String contentType() {
                return "application/json";
            }

            @Override
            public long contentLength() {
                return bytes.length;
            }

            @Override
            public void writeTo(OutputStream out) throws IOException {
                out.write(bytes);
            }
        };
    }
}
