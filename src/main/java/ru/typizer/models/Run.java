package ru.typizer.models;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ru.typizer.internal.Json;
import ru.typizer.internal.OutputMapper;

/**
 * A run: one document recognized by one schema. {@code extract()}, {@code runs().get()}, the list of
 * runs and webhook events all return this same type.
 *
 * <p>{@link #output()} and {@link #fields()} are {@code null} while the run is not finished, when it
 * failed, in list items (the list does not return them) and when the result was deleted after the
 * retention period ({@link #outputExpired()}). Reading them never throws.
 */
public final class Run extends TypizerObject {
    static final Set<String> FIELDS = fields("object", "id", "status", "typizer_id", "typizer_version", "mode",
            "format", "source", "pages", "output", "fields", "output_expired", "error", "file_id", "file", "batch_id",
            "metadata", "catalog", "checks_used", "created_at", "completed_at");

    private volatile Map<String, FieldResult> fieldResults;

    public Run(Map<String, Object> data) {
        super(data, FIELDS);
    }

    /** {@code run_…}. */
    public String id() {
        return str("id");
    }

    /** {@code queued}, {@code processing}, {@code succeeded} or {@code failed}. */
    public RunStatus status() {
        return enumValue("status", RunStatus.class);
    }

    /** The raw status (also for values newer than this SDK). */
    public String statusValue() {
        return str("status");
    }

    /** The run is finished: {@code succeeded} or {@code failed}. An unknown status is not final. */
    public boolean isTerminal() {
        RunStatus s = status();
        return s == RunStatus.SUCCEEDED || s == RunStatus.FAILED;
    }

    /** The typizer of the run ({@code tz_…}), or null for a one-off schema or a catalog schema. */
    public String typizerId() {
        return str("typizer_id");
    }

    public Integer typizerVersion() {
        return integer("typizer_version");
    }

    public Mode mode() {
        return enumValue("mode", Mode.class);
    }

    public String modeValue() {
        return str("mode");
    }

    public Format format() {
        return enumValue("format", Format.class);
    }

    public String formatValue() {
        return str("format");
    }

    /** Where the run came from: {@code api}, {@code my}, {@code mcp}, … */
    public String source() {
        return str("source");
    }

    /** Pages of the document and pages billed. */
    public Pages pages() {
        return object("pages", Pages::new);
    }

    /**
     * The result by your schema: keys and their order as in the schema, numbers as {@link Long} or
     * {@link java.math.BigDecimal}, objects as {@code Map}, arrays as {@code List}. {@code null} when
     * there is no result (see the class description).
     */
    public Map<String, Object> output() {
        return map("output");
    }

    /** {@link #output()} as JSON text, for your own JSON library; {@code null} when there is no result. */
    public String outputJson() {
        Object v = get("output");
        return v == null ? null : Json.write(v);
    }

    /**
     * {@link #output()} as your own class, through the JSON library of the application: Jackson
     * ({@code jackson-databind} 2.x; unknown properties are ignored) or Gson, whichever is on the
     * classpath. {@code null} when there is no result.
     *
     * @throws IllegalStateException if neither Jackson nor Gson is available, or the output does not fit the class
     */
    public <T> T outputAs(Class<T> type) {
        String json = outputJson();
        return json == null ? null : OutputMapper.read(json, type);
    }

    /**
     * Check results per leaf field, by path in the API notation ({@code seller.inn},
     * {@code items[0].amount}), in the order of the output; {@code null} when there is no result.
     */
    public Map<String, FieldResult> fields() {
        Map<String, FieldResult> cached = fieldResults;
        if (cached != null) return cached;
        Map<String, Object> raw = map("fields");
        if (raw == null) return null;
        Map<String, FieldResult> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            if (e.getValue() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> f = (Map<String, Object>) e.getValue();
                out.put(e.getKey(), new FieldResult(e.getKey(), f));
            }
        }
        cached = Collections.unmodifiableMap(out);
        fieldResults = cached;
        return cached;
    }

    /** The run succeeded, but at least one field is not {@code ok}: a person should look at it. */
    public boolean needsReview() {
        return status() == RunStatus.SUCCEEDED && !fieldsToReview().isEmpty();
    }

    /**
     * Fields with status {@code warn} or {@code missing}, in the order of {@link #fields()}; each
     * knows its {@link FieldResult#path() path}. Empty when there is nothing to review or no result.
     */
    public List<FieldResult> fieldsToReview() {
        Map<String, FieldResult> all = fields();
        if (all == null) return Collections.emptyList();
        List<FieldResult> out = new ArrayList<>();
        for (FieldResult f : all.values()) {
            if (f.status() != FieldStatus.OK) out.add(f);
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Fields whose value failed a check (status {@code warn}), in the order of {@link #fields()}: path,
     * the message to show a person and the reason code. {@code missing} fields are not here — they
     * have no value to check. Empty when there are none or there is no result.
     */
    public List<FieldWarning> warnings() {
        Map<String, FieldResult> all = fields();
        if (all == null) return Collections.emptyList();
        List<FieldWarning> out = new ArrayList<>();
        for (FieldResult f : all.values()) {
            if (f.status() == FieldStatus.WARN) out.add(new FieldWarning(f.path(), f.message(), f.reason()));
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * The checks a run of a one-off schema used: references on their exact versions and checks
     * written into the schema ({@code ref = "inline"}, with {@link RunCheckUsed#field()}). {@code null}
     * for a typizer's run (its checks are in {@link Typizer#checks()}) and in webhooks sent before the
     * field existed.
     */
    public List<RunCheckUsed> checksUsed() {
        if (!(get("checks_used") instanceof List)) return null;
        return objects("checks_used", RunCheckUsed::new);
    }

    /** The result was deleted after the retention period: {@link #output()} and {@link #fields()} are null. */
    public boolean outputExpired() {
        return flag("output_expired");
    }

    /** Why the run failed; {@code null} unless {@code failed}. */
    public ErrorInfo error() {
        return object("error", ErrorInfo::new);
    }

    /** The document ({@code file_…}). */
    public String fileId() {
        return str("file_id");
    }

    /** Name and type of the document. */
    public FileInfo file() {
        return object("file", FileInfo::new);
    }

    public String batchId() {
        return str("batch_id");
    }

    /** Your metadata, as sent when the run was created. */
    public Map<String, String> metadata() {
        return stringMap("metadata");
    }

    /** The catalog schema of the run, if it ran one. */
    public CatalogRef catalog() {
        return object("catalog", CatalogRef::new);
    }

    public OffsetDateTime createdAt() {
        return dateTime("created_at");
    }

    public OffsetDateTime completedAt() {
        return dateTime("completed_at");
    }

    /** {@code pages} of a run. */
    public static final class Pages extends TypizerObject {
        static final Set<String> FIELDS = fields("physical", "billed");

        public Pages(Map<String, Object> data) {
            super(data, FIELDS);
        }

        /** Pages in the document. */
        public Integer physical() {
            return integer("physical");
        }

        /** Pages billed for the run. */
        public Integer billed() {
            return integer("billed");
        }
    }

    /** {@code error} of a failed run. */
    public static final class ErrorInfo extends TypizerObject {
        static final Set<String> FIELDS = fields("code", "message");

        public ErrorInfo(Map<String, Object> data) {
            super(data, FIELDS);
        }

        /** {@code document_unreadable}, {@code too_many_pages}, {@code extraction_failed}, {@code timeout}, … */
        public String code() {
            return str("code");
        }

        public String message() {
            return str("message");
        }
    }

    /** {@code file} of a run. */
    public static final class FileInfo extends TypizerObject {
        static final Set<String> FIELDS = fields("filename", "mime");

        public FileInfo(Map<String, Object> data) {
            super(data, FIELDS);
        }

        public String filename() {
            return str("filename");
        }

        public String mime() {
            return str("mime");
        }
    }

    /** {@code catalog} of a run. */
    public static final class CatalogRef extends TypizerObject {
        static final Set<String> FIELDS = fields("entry_id", "ref", "version");

        public CatalogRef(Map<String, Object> data) {
            super(data, FIELDS);
        }

        public String entryId() {
            return str("entry_id");
        }

        public String ref() {
            return str("ref");
        }

        public String version() {
            return str("version");
        }
    }
}
