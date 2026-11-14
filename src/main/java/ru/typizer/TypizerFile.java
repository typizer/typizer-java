package ru.typizer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import ru.typizer.models.File;

/**
 * A document for {@code extract()}, {@code runs().create()}, {@code files().upload()} and
 * {@code batches().create()} — five explicit ways, never guessed from a string:
 *
 * <ul>
 *   <li>{@link #fromPath(Path)} — a local file, sent in the same request ({@code multipart/form-data})
 *       and read from disk again for a retry;
 *   <li>{@link #fromBytes(byte[], String)} — bytes and a file name;
 *   <li>{@link #fromStream(InputStream, String)} — a stream and a file name; the stream is read into
 *       memory once (at most 50 MiB, the API limit), so a retry can send it again;
 *   <li>{@link #fromUrl(String)} — a public URL the API downloads ({@code url});
 *   <li>{@link #fromId(String)} — a file uploaded before ({@code file_id}).
 * </ul>
 */
public final class TypizerFile {
    /** The API's limit for one file: 50 MiB. */
    public static final long MAX_BYTES = 52_428_800L;

    enum Kind {
        PATH,
        BYTES,
        STREAM,
        URL,
        ID
    }

    private static final Map<String, String> TYPES = new HashMap<>();

    static {
        String[][] t = {
            {"pdf", "application/pdf"}, {"png", "image/png"}, {"jpg", "image/jpeg"}, {"jpeg", "image/jpeg"},
            {"tif", "image/tiff"}, {"tiff", "image/tiff"}, {"webp", "image/webp"}, {"heic", "image/heic"},
            {"doc", "application/msword"},
            {"docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"},
            {"xls", "application/vnd.ms-excel"},
            {"xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"},
            {"odt", "application/vnd.oasis.opendocument.text"},
            {"ods", "application/vnd.oasis.opendocument.spreadsheet"}, {"rtf", "application/rtf"},
            {"txt", "text/plain"}, {"zip", "application/zip"},
        };
        for (String[] row : t) TYPES.put(row[0], row[1]);
    }

    private final Kind kind;
    private final Path path;
    private final InputStream stream;
    private final String value; // url or file id
    private final String filename;
    private byte[] bytes; // BYTES, or STREAM once read

    private TypizerFile(Kind kind, Path path, byte[] bytes, InputStream stream, String value, String filename) {
        this.kind = kind;
        this.path = path;
        this.bytes = bytes;
        this.stream = stream;
        this.value = value;
        this.filename = filename;
    }

    /** A local file; its name and type go with it. */
    public static TypizerFile fromPath(Path path) {
        if (path == null) throw new IllegalArgumentException("path must not be null");
        Path name = path.getFileName();
        return new TypizerFile(Kind.PATH, path, null, null, null, name == null ? "document" : name.toString());
    }

    /** A local file by its path. */
    public static TypizerFile fromPath(String path) {
        if (path == null) throw new IllegalArgumentException("path must not be null");
        return fromPath(Paths.get(path));
    }

    /** A local file ({@code java.io.File}). */
    public static TypizerFile fromFile(java.io.File file) {
        if (file == null) throw new IllegalArgumentException("file must not be null");
        return fromPath(file.toPath());
    }

    /** Bytes of a document, with its file name (the extension gives the {@code Content-Type}). */
    public static TypizerFile fromBytes(byte[] bytes, String filename) {
        if (bytes == null) throw new IllegalArgumentException("bytes must not be null");
        return new TypizerFile(Kind.BYTES, null, bytes, null, null, nameOr(filename));
    }

    /** Bytes of a document named {@code document}. */
    public static TypizerFile fromBytes(byte[] bytes) {
        return fromBytes(bytes, null);
    }

    /**
     * A stream with a document. It is read into memory on first use (at most 50 MiB) and is not
     * closed by the SDK.
     */
    public static TypizerFile fromStream(InputStream stream, String filename) {
        if (stream == null) throw new IllegalArgumentException("stream must not be null");
        return new TypizerFile(Kind.STREAM, null, null, stream, null, nameOr(filename));
    }

    /** A stream with a document named {@code document}. */
    public static TypizerFile fromStream(InputStream stream) {
        return fromStream(stream, null);
    }

    /** A public {@code http(s)} URL the API downloads the document from. */
    public static TypizerFile fromUrl(String url) {
        if (url == null || url.isEmpty()) throw new IllegalArgumentException("url must not be empty");
        return new TypizerFile(Kind.URL, null, null, null, url, null);
    }

    public static TypizerFile fromUrl(URL url) {
        if (url == null) throw new IllegalArgumentException("url must not be null");
        return fromUrl(url.toString());
    }

    /** A file uploaded with {@code files().upload()} ({@code file_…}). */
    public static TypizerFile fromId(String fileId) {
        if (fileId == null || fileId.isEmpty()) throw new IllegalArgumentException("fileId must not be empty");
        return new TypizerFile(Kind.ID, null, null, null, fileId, null);
    }

    /** A file uploaded with {@code files().upload()}. */
    public static TypizerFile from(File file) {
        if (file == null) throw new IllegalArgumentException("file must not be null");
        return fromId(file.id());
    }

    private static String nameOr(String filename) {
        return filename == null || filename.isEmpty() ? "document" : filename;
    }

    Kind kind() {
        return kind;
    }

    /** Whether the document goes in the request body (path, bytes, stream). */
    boolean isUpload() {
        return kind == Kind.PATH || kind == Kind.BYTES || kind == Kind.STREAM;
    }

    /** The URL ({@link #fromUrl}) or file id ({@link #fromId}). */
    String value() {
        return value;
    }

    Path path() {
        return path;
    }

    /** File name for {@code multipart/form-data}. */
    public String filename() {
        return filename;
    }

    /** {@code Content-Type} by the file extension, {@code application/octet-stream} otherwise. */
    public String contentType() {
        return contentTypeOf(filename);
    }

    static String contentTypeOf(String filename) {
        if (filename == null) return "application/octet-stream";
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return "application/octet-stream";
        String t = TYPES.get(filename.substring(dot + 1).toLowerCase(Locale.ROOT));
        return t != null ? t : "application/octet-stream";
    }

    /** Size in bytes (reads a stream into memory). */
    long size() {
        try {
            if (kind == Kind.PATH) return Files.size(path);
            return content().length;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + filename, e);
        }
    }

    /** The bytes of a BYTES or STREAM document (a stream is read once). */
    synchronized byte[] content() throws IOException {
        if (bytes != null) return bytes;
        if (kind != Kind.STREAM) throw new IllegalStateException(kind + " has no in-memory content");
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[64 * 1024];
        long total = 0;
        for (int n; (n = stream.read(chunk)) != -1; ) {
            total += n;
            if (total > MAX_BYTES) throw new FileTooLarge(total);
            buf.write(chunk, 0, n);
        }
        bytes = buf.toByteArray();
        return bytes;
    }

    /** Thrown inside {@link #content()} when a stream is over the limit; the client turns it into a 413 error. */
    static final class FileTooLarge extends IOException {
        private static final long serialVersionUID = 1L;

        FileTooLarge(long size) {
            super("The file is larger than " + MAX_BYTES + " bytes (" + size + "+)");
        }
    }

    @Override
    public String toString() {
        switch (kind) {
            case URL: return "TypizerFile{url}";
            case ID: return "TypizerFile{" + value + "}";
            default: return "TypizerFile{" + kind.name().toLowerCase(Locale.ROOT) + "}";
        }
    }
}
