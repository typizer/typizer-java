package ru.typizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ru.typizer.MockServer.header;
import static ru.typizer.MockServer.map;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ru.typizer.errors.ApiException;
import ru.typizer.errors.AuthenticationException;
import ru.typizer.errors.BadRequestException;
import ru.typizer.errors.ConflictException;
import ru.typizer.errors.ConnectionException;
import ru.typizer.errors.FileTooLargeException;
import ru.typizer.errors.IdempotencyConflictException;
import ru.typizer.errors.InsufficientBalanceException;
import ru.typizer.errors.NotFoundException;
import ru.typizer.errors.PermissionDeniedException;
import ru.typizer.errors.RateLimitException;
import ru.typizer.errors.RunFailedException;
import ru.typizer.errors.ServiceUnavailableException;
import ru.typizer.errors.TimeoutException;
import ru.typizer.errors.WaitTimeoutException;
import ru.typizer.http.HttpRequest;
import ru.typizer.http.HttpResponse;
import ru.typizer.http.HttpTransport;
import ru.typizer.http.UrlConnectionTransport;
import ru.typizer.internal.Json;
import ru.typizer.models.Balance;
import ru.typizer.models.CatalogEntryBrief;
import ru.typizer.models.CheckStatus;
import ru.typizer.models.CatalogEntry;
import ru.typizer.models.Deleted;
import ru.typizer.models.FieldCheck;
import ru.typizer.models.FieldResult;
import ru.typizer.models.FieldWarning;
import ru.typizer.models.RunCheckUsed;
import ru.typizer.models.Validator;
import ru.typizer.models.ValidatorBump;
import ru.typizer.models.ValidatorDetail;
import ru.typizer.models.ValidatorSuggestion;
import ru.typizer.models.ValidatorTest;
import ru.typizer.models.ValidatorTestResult;
import ru.typizer.models.ValidatorUpdateResult;
import ru.typizer.models.ValidatorVersion;
import ru.typizer.models.FieldSource;
import ru.typizer.models.FieldStatus;
import ru.typizer.models.Me;
import ru.typizer.models.Run;
import ru.typizer.models.RunStatus;
import ru.typizer.models.Typizer;
import ru.typizer.models.WebhookEvent;
import ru.typizer.models.WebhookEventType;

/**
 * The Java SDK against the SDK conformance mock: every check {@code SC-xx} of
 * packages/sdk-spec/conformance/SCENARIOS.md (SC-32, webhook vectors, is in {@link WebhookTest}).
 */
class ConformanceTest {
    static final String UUID_V4 = "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$";
    static final String SAMPLE_URL = "https://typizer.ru/samples/invoice.pdf";
    static final byte[] PDF = "%PDF-1.4\n% test\n".getBytes(StandardCharsets.US_ASCII);

    static Map<String, Object> schema() {
        Map<String, Object> inn = new LinkedHashMap<>();
        inn.put("type", "string");
        inn.put("x-typizer-check", "inn");
        Map<String, Object> sellerProps = new LinkedHashMap<>();
        sellerProps.put("inn", inn);
        Map<String, Object> seller = new LinkedHashMap<>();
        seller.put("type", "object");
        seller.put("properties", sellerProps);
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("invoice_number", Collections.singletonMap("type", "string"));
        props.put("total", Collections.singletonMap("type", "number"));
        props.put("seller", seller);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        List<String> required = new ArrayList<>();
        required.add("invoice_number");
        required.add("total");
        schema.put("required", required);
        return schema;
    }

    static ExtractParams.Builder byUrl() {
        return ExtractParams.builder().url(SAMPLE_URL).schema(schema());
    }

    /** A logger that keeps every line. */
    static final class Lines implements TypizerLogger {
        final List<String> all = new CopyOnWriteArrayList<>();
        final List<String> warn = new CopyOnWriteArrayList<>();

        @Override
        public void log(Level level, String message) {
            all.add(level + " " + message);
            if (level == Level.WARN) warn.add(message);
        }
    }

    private static void assertSucceeded(Run run) {
        assertEquals(RunStatus.SUCCEEDED, run.status());
        assertEquals("А-1024", run.output().get("invoice_number"));
        assertEquals(3660L, ((Number) run.output().get("total")).longValue());
        assertEquals("7707083893", map(run.output().get("seller")).get("inn"));
        assertEquals(3, run.fields().size());
        for (FieldResult f : run.fields().values()) assertEquals(FieldStatus.OK, f.status());
        assertFalse(run.needsReview());
    }

    private static Map<String, Object> onlyFile(Map<String, Object> request) {
        Map<String, Object> body = map(request.get("body"));
        assertEquals("multipart", body.get("type"));
        @SuppressWarnings("unchecked")
        List<Object> files = (List<Object>) body.get("files");
        assertEquals(1, files.size());
        return map(files.get(0));
    }

    // ---------------------------------------------------------------- tz_test_ok

    @Test
    @DisplayName("SC-01 extract from path bytes and stream")
    void sc01_extractFromPathBytesAndStream(@TempDir Path dir) throws IOException {
        Path pdf = dir.resolve("invoice.pdf");
        java.nio.file.Files.write(pdf, PDF);

        String k1 = MockServer.key("ok");
        assertSucceeded(MockServer.client(k1).extract(pdf, schema()));
        String k2 = MockServer.key("ok");
        assertSucceeded(MockServer.client(k2).extract(TypizerFile.fromBytes(PDF, "scan.png"), schema()));
        String k3 = MockServer.key("ok");
        assertSucceeded(MockServer.client(k3).extract(TypizerFile.fromStream(new ByteArrayInputStream(PDF), "act.docx"), schema()));
        String k4 = MockServer.key("ok");
        assertSucceeded(MockServer.client(k4).extract(new ByteArrayInputStream(PDF), schema()));

        String[][] expected = {
            {k1, "invoice.pdf", "application/pdf"},
            {k2, "scan.png", "image/png"},
            {k3, "act.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"},
            {k4, "document", "application/octet-stream"},
        };
        for (String[] e : expected) {
            List<Map<String, Object>> log = MockServer.requests(e[0]);
            assertEquals(1, log.size(), "one request for " + e[1]);
            Map<String, Object> r = log.get(0);
            assertEquals("extract", r.get("operation_id"));
            assertEquals("POST", r.get("method"));
            Map<String, Object> file = onlyFile(r);
            assertEquals("file", file.get("field"));
            assertEquals(e[1], file.get("filename"));
            assertEquals(e[2], file.get("content_type"));
            assertEquals((long) PDF.length, ((Number) file.get("size")).longValue());
            Map<String, Object> fields = map(map(r.get("body")).get("fields"));
            assertEquals(Json.write(schema()), fields.get("schema"), "schema goes as JSON text, key order kept");
            int wait = Integer.parseInt((String) map(r.get("query")).get("wait"));
            assertTrue(wait > 0 && wait <= 60);
        }
    }

    @Test
    @DisplayName("SC-02 extract with catalog schema and typizer")
    void sc02_extractWithCatalogSchemaAndTypizer() {
        String key = MockServer.key("ok");
        TypizerClient client = MockServer.client(key);
        Run byCatalog = client.extract(ExtractParams.builder().url(SAMPLE_URL).catalog("schet-faktura@1").build());
        assertEquals(RunStatus.SUCCEEDED, byCatalog.status());
        assertEquals("schet-faktura", byCatalog.catalog().ref().replaceAll("@.*", ""));

        Typizer typizer = client.typizers().list().data().get(0);
        Run byTypizer = client.extract(ExtractParams.builder().url(SAMPLE_URL).typizer(typizer).build());
        assertEquals(RunStatus.SUCCEEDED, byTypizer.status());
        assertEquals(typizer.id(), byTypizer.typizerId());

        Map<String, Object> catalogCall = MockServer.requests(key, "extract").get(0);
        assertEquals("schet-faktura@1", map(map(catalogCall.get("body")).get("value")).get("catalog"));
        Map<String, Object> runCall = MockServer.requests(key, "createRun").get(0);
        assertEquals("/v1/typizers/" + typizer.id() + "/runs", runCall.get("path"));
        assertFalse(map(map(runCall.get("body")).get("value")).containsKey("schema"));
    }

    @Test
    @DisplayName("SC-03 headers of every request")
    void sc03_headersOfEveryRequest() {
        String key = MockServer.key("ok");
        TypizerClient client = MockServer.client(key);
        client.me();
        client.extract(byUrl().build());
        client.typizers().list();
        List<Map<String, Object>> log = MockServer.requests(key);
        assertEquals(3, log.size());
        for (Map<String, Object> r : log) {
            assertEquals("Bearer " + key, header(r, "authorization"));
            assertTrue(header(r, "user-agent").matches("^typizer-java/" + TypizerClient.VERSION.replace(".", "\\.")
                    + " \\(java/[^;]+; [a-z0-9-]+/[^)]+\\)$"), header(r, "user-agent"));
            assertEquals("application/json", header(r, "accept"));
        }
    }

    @Test
    @DisplayName("SC-04 idempotency key on every post")
    void sc04_idempotencyKeyOnEveryPost() {
        String key = MockServer.key("ok");
        TypizerClient client = MockServer.client(key);
        client.typizers().create("Счета", schema());
        client.typizers().create("Акты", schema());
        client.typizers().create(TypizerParams.builder().name("Свой ключ").schema(schema()).build(),
                RequestOptions.builder().idempotencyKey("invoice-42 from queue").build());
        client.me();
        List<Map<String, Object>> posts = MockServer.requests(key, "createTypizer");
        String a = header(posts.get(0), "idempotency-key");
        String b = header(posts.get(1), "idempotency-key");
        assertTrue(a.matches(UUID_V4), a);
        assertTrue(b.matches(UUID_V4), b);
        assertNotEquals(a, b);
        assertEquals("invoice-42 from queue", header(posts.get(2), "idempotency-key"));
        assertNull(header(MockServer.requests(key, "getMe").get(0), "idempotency-key"));
    }

    @Test
    @DisplayName("SC-05 auto pagination of runs and catalog")
    void sc05_autoPaginationOfRunsAndCatalog() {
        String key = MockServer.key("ok");
        TypizerClient client = MockServer.client(key);
        Page<Run> first = client.runs().list(RunListParams.builder().limit(10).build());
        List<String> ids = new ArrayList<>();
        for (Run run : first.autoPager()) ids.add(run.id());
        assertEquals(25, ids.size());
        assertEquals(25, new java.util.HashSet<>(ids).size());
        List<Map<String, Object>> calls = MockServer.requests(key, "listRuns");
        assertEquals(3, calls.size());
        assertNull(map(calls.get(0).get("query")).get("starting_after"));
        assertEquals(first.nextCursor(), map(calls.get(1).get("query")).get("starting_after"));
        for (Map<String, Object> c : calls) assertEquals("10", map(c.get("query")).get("limit"));

        List<CatalogEntryBrief> entries = new ArrayList<>();
        client.catalog().list(CatalogListParams.builder().limit(1).build()).autoPager().forEach(entries::add);
        assertEquals(3, entries.size());
        List<Map<String, Object>> catalogCalls = MockServer.requests(key, "listCatalog");
        assertEquals(3, catalogCalls.size());
        assertNotNull(map(catalogCalls.get(1).get("query")).get("cursor"));
        assertNull(map(catalogCalls.get(1).get("query")).get("starting_after"));
    }

    @Test
    @DisplayName("SC-06 me balance and not found")
    void sc06_meBalanceAndNotFound() {
        String key = MockServer.key("ok");
        TypizerClient client = MockServer.client(key);
        Me me = client.me();
        assertEquals("api_key", me.auth());
        assertNotNull(me.organization().id());
        assertNotNull(me.apiKey());
        Balance balance = client.balance();
        assertEquals(950L, balance.pagesAvailable().longValue());
        assertFalse(balance.lots().isEmpty());

        NotFoundException e = assertThrows(NotFoundException.class, () -> client.runs().get("run_00000000000000000000000000"));
        assertEquals(404, e.statusCode());
        assertEquals("not_found", e.code());
        assertNotNull(e.requestId());
        assertFalse(e.isRetryable());
    }

    @Test
    @DisplayName("SC-07 typizer crud")
    void sc07_typizerCrud() {
        String key = MockServer.key("ok");
        TypizerClient client = MockServer.client(key);
        Typizer created = client.typizers().create("Счета поставщиков (Java)", schema());
        assertTrue(created.id().startsWith("tz_"));
        assertEquals(new ArrayList<>(schema().keySet()), new ArrayList<>(created.schema().keySet()));
        assertEquals(created.id(), client.typizers().get(created.id()).id());
        Typizer updated = client.typizers().update(created.id(), TypizerParams.builder().name("Счета (новое имя)").build());
        assertEquals("Счета (новое имя)", updated.name());
        boolean listed = false;
        for (Typizer t : client.typizers().list().autoPager()) listed |= t.id().equals(created.id());
        assertTrue(listed);
        Deleted deleted = client.typizers().delete(created.id());
        assertTrue(deleted.deleted());
        assertThrows(NotFoundException.class, () -> client.typizers().get(created.id()));
        Map<String, Object> patch = MockServer.requests(key, "updateTypizer").get(0);
        assertEquals("PATCH", patch.get("method"));
        assertNull(header(patch, "idempotency-key"));
    }

    @Test
    @DisplayName("SC-08 unknown fields and enum values do not break parsing")
    void sc08_unknownFieldsAndEnumValuesDoNotBreakParsing() {
        Map<String, Object> run = Json.parseObject("{\"id\":\"run_1\",\"object\":\"run\",\"status\":\"archived\","
                + "\"mode\":\"turbo\",\"brand_new\":{\"x\":1},\"fields\":{\"a\":{\"status\":\"info\",\"sources\":[]}},"
                + "\"output\":{\"a\":1},\"created_at\":\"not a date\"}");
        Run r = new Run(run);
        assertEquals(RunStatus.UNKNOWN, r.status());
        assertEquals("archived", r.statusValue());
        assertFalse(r.isTerminal(), "an unknown status is not final");
        assertEquals("turbo", r.modeValue());
        assertEquals(Collections.singletonMap("x", 1L), r.extra().get("brand_new"));
        assertEquals(FieldStatus.UNKNOWN, r.fields().get("a").status());
        assertEquals("info", r.fields().get("a").statusValue());
        assertNull(r.createdAt());
        assertNull(r.error());
        assertNull(r.completedAt());

        WebhookEvent e = new WebhookEvent(Json.parseObject("{\"id\":\"evt_1\",\"type\":\"run.archived\",\"data\":{\"object\":{}}}"));
        assertEquals(WebhookEventType.UNKNOWN, e.type());
        assertEquals("run.archived", e.typeValue());
    }

    // ---------------------------------------------------------------- results

    @Test
    @DisplayName("SC-10 needs review")
    void sc10_needsReview() {
        Run run = MockServer.client(MockServer.key("needs_review")).extract(byUrl().build());
        assertTrue(run.needsReview());
        List<FieldResult> review = run.fieldsToReview();
        assertEquals(2, review.size());
        FieldResult first = review.get(0);
        assertEquals("invoice_number", first.path());
        assertEquals(FieldStatus.WARN, first.status());
        assertEquals("checksum_mismatch", first.reason());
        FieldSource src = first.sources().get(0);
        assertEquals(1, src.page().intValue());
        assertEquals(4, src.bbox().size());
        assertNotNull(src.text());
        FieldResult last = review.get(1);
        assertEquals("seller.inn", last.path());
        assertEquals(FieldStatus.MISSING, last.status());
        assertNull(last.reason());
        assertTrue(last.sources().isEmpty());
        assertNull(map(run.output().get("seller")).get("inn"));
    }

    @Test
    @DisplayName("SC-11 run failed")
    void sc11_runFailed() {
        TypizerClient client = MockServer.client(MockServer.key("run_failed"));
        RunFailedException e = assertThrows(RunFailedException.class, () -> client.extract(byUrl().build()));
        assertEquals("document_unreadable", e.code());
        assertNotNull(e.errorMessage());
        assertEquals(RunStatus.FAILED, e.run().status());
        assertFalse(e.isRetryable());
        assertTrue(e.getMessage().startsWith("[document_unreadable] "));
        Run waited = client.runs().waitFor(e.run().id());
        assertEquals(RunStatus.FAILED, waited.status());
        assertEquals("document_unreadable", waited.error().code());
    }

    @Test
    @DisplayName("SC-12 output expired")
    void sc12_outputExpired() {
        TypizerClient client = MockServer.client(MockServer.key("output_expired"));
        Run run = client.extract(byUrl().build());
        assertEquals(RunStatus.SUCCEEDED, run.status());
        assertTrue(run.outputExpired());
        assertNull(run.output());
        assertNull(run.outputJson());
        assertNull(run.fields());
        assertFalse(run.needsReview());
        assertTrue(run.fieldsToReview().isEmpty());
        assertThrows(NotFoundException.class, () -> client.runs().output(run.id()));
    }

    /** The schema of SC-36: an own check by reference, an inline check, an official check. */
    static Map<String, Object> checksSchema(String contractCheck) {
        return Json.parseObject("{\"type\":\"object\",\"properties\":{"
                + "\"contract_no\":{\"type\":\"string\",\"x-typizer-check\":\"" + contractCheck + "\"},"
                + "\"spec_no\":{\"type\":\"string\",\"x-typizer-check\":{\"kind\":\"rules\",\"applies_to\":\"string\","
                + "\"rules\":[{\"type\":\"length\",\"min\":4}],\"message\":\"Номер спецификации короче 4 знаков\"}},"
                + "\"seller\":{\"type\":\"object\",\"properties\":{\"inn\":{\"type\":\"string\",\"x-typizer-check\":\"inn\"}}}}}");
    }

    @Test
    @DisplayName("SC-36 checks: FieldResult message and checks, run.warnings() and checksUsed()")
    void sc36_checks() {
        String key = MockServer.key("checks");
        TypizerClient client = MockServer.client(key);
        Map<String, Object> schema = checksSchema("my/contract-no@1");
        Run run = client.extract(ExtractParams.builder().url(SAMPLE_URL).schema(schema).build());

        FieldResult contract = run.fields().get("contract_no");
        assertEquals(FieldStatus.WARN, contract.status());
        assertEquals("pattern_mismatch", contract.reason());
        assertEquals("Номер договора не по шаблону ДГ-ГГГГ/NNNN", contract.message());
        assertEquals(1, contract.checks().size());
        FieldCheck check = contract.checks().get(0);
        assertEquals("my/contract-no", check.ref());
        assertEquals("1.0.0", check.version());
        assertEquals("Номер договора", check.name());
        assertEquals(CheckStatus.FAIL, check.status());
        assertEquals("fail", check.statusValue());
        assertEquals("pattern", check.rule());
        assertEquals("pattern_mismatch", check.reason());

        FieldResult inn = run.fields().get("seller.inn");
        assertEquals(FieldStatus.OK, inn.status());
        assertNull(inn.message());
        assertEquals("inn", inn.checks().get(0).ref());
        assertEquals(CheckStatus.PASS, inn.checks().get(0).status());

        List<FieldWarning> warnings = run.warnings();
        assertEquals(2, warnings.size());
        assertEquals(new FieldWarning("contract_no", "Номер договора не по шаблону ДГ-ГГГГ/NNNN", "pattern_mismatch"),
                warnings.get(0));
        assertEquals("spec_no", warnings.get(1).path());
        assertEquals("Номер спецификации короче 4 знаков", warnings.get(1).message());
        assertEquals("invalid_format", warnings.get(1).reason());

        List<RunCheckUsed> used = run.checksUsed();
        assertEquals(2, used.size());
        assertEquals("my/contract-no", used.get(0).ref());
        assertEquals("1.0.0", used.get(0).version());
        assertEquals("Номер договора", used.get(0).name());
        assertNull(used.get(0).field());
        assertEquals("inline", used.get(1).ref());
        assertNull(used.get(1).version());
        assertNull(used.get(1).name());
        assertEquals("spec_no", used.get(1).field());

        // The inline check went to the server exactly as written.
        Map<String, Object> sent = map(map(MockServer.requests(key, "extract").get(0).get("body")).get("value"));
        assertEquals(schema, sent.get("schema"));

        // A typizer's run: checksUsed() is null, the locked checks are on the typizer.
        Typizer t = client.typizers().create("Договоры", schema);
        assertEquals(1, t.checks().size());
        assertEquals("my/contract-no", t.checks().get(0).ref());
        assertEquals("1.0.0", t.checks().get(0).version());
        assertEquals("Номер договора", t.checks().get(0).name());
        Run typizerRun = client.extract(ExtractParams.builder().url(SAMPLE_URL).typizer(t).build());
        assertNull(typizerRun.checksUsed());

        // Runs and webhooks made before 1.2: no checks_used, no message, no checks.
        Run old = new Run(Json.parseObject("{\"id\":\"run_1\",\"status\":\"succeeded\",\"fields\":{"
                + "\"a\":{\"status\":\"warn\",\"reason\":\"checksum_mismatch\",\"sources\":[]},"
                + "\"b\":{\"status\":\"missing\",\"sources\":[]}}}"));
        assertNull(old.checksUsed());
        assertEquals(1, old.warnings().size());
        assertEquals(new FieldWarning("a", null, "checksum_mismatch"), old.warnings().get(0));
        assertTrue(old.fields().get("a").checks().isEmpty());
        assertTrue(new Run(Json.parseObject("{\"id\":\"run_1\"}")).warnings().isEmpty());
    }

    @Test
    @DisplayName("SC-37 own validators: list, get by slug and id, create, update bump, red test, taken slug, archive")
    void sc37_validators() {
        String key = MockServer.key("ok");
        TypizerClient client = MockServer.client(key);

        Validator seeded = null;
        for (Validator v : client.validators().list().autoPager()) if ("my/contract-no".equals(v.ref())) seeded = v;
        assertNotNull(seeded);
        assertEquals("contract-no", seeded.slug());
        assertEquals("Номер договора", seeded.name());
        assertEquals("1.0.0", seeded.version().version());

        ValidatorDetail bySlug = client.validators().get("my/contract-no@1");
        List<Map<String, Object>> slugCalls = MockServer.requests(key, "getValidatorBySlug");
        assertEquals("/v1/validators/my/contract-no", slugCalls.get(0).get("path"));
        assertEquals(seeded.id(), bySlug.id());
        assertEquals(1, bySlug.versions().size());
        ValidatorVersion v1 = bySlug.versions().get(0);
        assertEquals("rules", v1.spec().get("kind"));
        assertEquals(3, v1.tests().size());
        assertEquals(ValidatorTest.EXPECT_PASS, v1.tests().get(1).expect());
        assertEquals("регистр не важен", v1.tests().get(1).note());
        assertNotNull(bySlug.usedBy());

        ValidatorDetail byId = client.validators().get(seeded.id());
        assertEquals("/v1/validators/" + seeded.id(), MockServer.requests(key, "getValidator").get(0).get("path"));
        assertEquals(bySlug.versions().size(), byId.versions().size());
        assertEquals(1, client.validators().versions("my/contract-no").size());

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("kind", "rules");
        spec.put("applies_to", "string");
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("type", "pattern");
        rule.put("pattern", "СП-\\d{4}");
        spec.put("rules", Collections.singletonList(rule));
        spec.put("message", "Номер спецификации не по шаблону СП-ГГГГ");
        Validator created = client.validators().create(ValidatorParams.builder().name("Номер спецификации").slug("spec-no")
                .spec(spec).tests(ValidatorTest.pass("СП-2026"), ValidatorTest.fail("СП-26")).build());
        assertEquals("my/spec-no", created.ref());
        assertEquals("1.0.0", created.version().version());
        assertEquals(spec, created.version().spec());
        Map<String, Object> createBody = map(map(MockServer.requests(key, "createValidator").get(0).get("body")).get("value"));
        assertEquals(spec, createBody.get("spec"));

        ValidatorUpdateResult updated = client.validators().update(created.id(), ValidatorParams.builder()
                .tests(ValidatorTest.pass("СП-2026"), ValidatorTest.fail("СП-26"), ValidatorTest.pass("СП-2027")).build());
        assertEquals(ValidatorBump.MINOR, updated.bump());
        assertEquals("minor", updated.bumpValue());
        assertEquals("1.1.0", updated.validator().version().version());
        assertFalse(updated.reasons().isEmpty());

        BadRequestException red = assertThrows(BadRequestException.class, () -> client.validators().create(
                ValidatorParams.builder().name("Красный").spec(spec).tests(ValidatorTest.fail("СП-2026")).build()));
        assertEquals("tests/0", red.param());
        assertThrows(ConflictException.class,
                () -> client.validators().create(ValidatorParams.builder().name("Ещё").slug("spec-no").spec(spec).build()));

        Typizer t = client.typizers().create("Спецификации", checksSchema("my/spec-no@1"));
        ConflictException inUse = assertThrows(ConflictException.class, () -> client.validators().archive(created.id()));
        assertEquals("used_by", inUse.param());
        assertEquals(t.id(), client.validators().get("my/spec-no").usedBy().get(0).typizerId());

        Validator spare = client.validators().create("Запасная", spec);
        Deleted archived = client.validators().archive(spare.id());
        assertTrue(archived.deleted());
        assertEquals(spare.id(), archived.id());

        // Argument errors before the network.
        int before = MockServer.requests(key).size();
        assertThrows(IllegalArgumentException.class, () -> client.validators().create(ValidatorParams.builder().name("x").build()));
        assertThrows(IllegalArgumentException.class, () -> client.validators().get(" "));
        assertEquals(before, MockServer.requests(key).size());
    }

    @Test
    @DisplayName("SC-38 validators.test with a reference and with a spec; argument and reference errors")
    void sc38_validatorTest() {
        String key = MockServer.key("ok");
        TypizerClient client = MockServer.client(key);

        ValidatorTestResult r = client.validators().test(Arrays.asList("ДГ-2026/0153", "ДГ-26/153"), "my/contract-no@1");
        assertNotNull(r.explain());
        assertEquals(2, r.results().size());
        ValidatorTestResult.Result ok = r.results().get(0);
        assertEquals("ДГ-2026/0153", ok.value());
        assertTrue(ok.pass());
        assertNull(ok.reason());
        assertNull(ok.message());
        assertNull(ok.ruleIndex());
        ValidatorTestResult.Result bad = r.results().get(1);
        assertFalse(bad.pass());
        assertEquals("pattern_mismatch", bad.reason());
        assertEquals("Номер договора не по шаблону ДГ-ГГГГ/NNNN", bad.message());
        assertEquals(Integer.valueOf(0), bad.ruleIndex());
        // Tested values are document data: never in toString().
        assertFalse(r.toString().contains("ДГ-26/153"));
        assertFalse(bad.toString().contains("ДГ-26/153"));

        Map<String, Object> innSpec = new LinkedHashMap<>();
        innSpec.put("kind", "native");
        innSpec.put("id", "inn");
        ValidatorTestResult bySpec = client.validators().test(
                ValidatorTestParams.builder().values("7707083893", "7707083894").spec(innSpec).build());
        assertTrue(bySpec.results().get(0).pass());
        assertFalse(bySpec.results().get(1).pass());
        assertNotNull(bySpec.results().get(1).reason());
        Map<String, Object> sent = map(map(MockServer.requests(key, "testValidator").get(1).get("body")).get("value"));
        assertEquals(innSpec, sent.get("spec"));
        assertFalse(sent.containsKey("ref"));

        int before = MockServer.requests(key).size();
        assertThrows(IllegalArgumentException.class, () -> ValidatorTestParams.builder().values("x").build());
        assertThrows(IllegalArgumentException.class,
                () -> ValidatorTestParams.builder().values("x").ref("inn@1").spec(innSpec).build());
        assertThrows(IllegalArgumentException.class, () -> client.validators().test(Collections.emptyList(), "inn@1"));
        assertThrows(IllegalArgumentException.class, () -> client.validators().test(Arrays.asList("x"), null));
        assertEquals(before, MockServer.requests(key).size());

        BadRequestException unknown = assertThrows(BadRequestException.class,
                () -> client.validators().test(Arrays.asList("x"), "my/nope@1"));
        assertEquals("ref", unknown.param());
    }

    @Test
    @DisplayName("SC-39 validators.suggest from examples and a field; nothing to go on is a BadRequest")
    void sc39_validatorSuggest() {
        String key = MockServer.key("ok");
        TypizerClient client = MockServer.client(key);
        ValidatorSuggestion s = client.validators().suggest(ValidatorSuggestParams.builder()
                .pass("ДГ-2024/0153", "ДГ-2023/0001").fail("ДГ-24/153").field("contract_no").build());
        assertEquals("rules", s.spec().get("kind"));
        assertEquals(3, s.tests().size());
        assertEquals("pass", s.tests().get(0).expect());
        assertEquals("fail", s.tests().get(2).expect());
        boolean allMatch = true;
        for (ValidatorSuggestion.SuggestedTest x : s.tests()) allMatch &= x.expect().equals(x.got());
        assertEquals(allMatch, s.converged());
        assertNotNull(s.explain());
        assertEquals("shape", s.source());
        assertNotNull(s.name());
        boolean own = false;
        for (ValidatorSuggestion.Candidate c : s.candidatesFromCatalog())
            own |= "own".equals(c.kind()) && "my/contract-no@1".equals(c.ref());
        assertTrue(own, "candidates: " + s.candidatesFromCatalog());
        Map<String, Object> sent = map(map(MockServer.requests(key, "suggestValidator").get(0).get("body")).get("value"));
        assertEquals(Arrays.asList("ДГ-2024/0153", "ДГ-2023/0001"), map(sent.get("examples")).get("pass"));
        assertEquals("contract_no", map(sent.get("field")).get("name"));
        assertFalse(sent.containsKey("description"));

        assertThrows(BadRequestException.class, () -> client.validators().suggest(ValidatorSuggestParams.builder().build()));
    }

    @Test
    @DisplayName("Validator catalog: list and get with type=validator, the published tests, fork into my/<slug>")
    void validatorCatalog() {
        TypizerClient client = MockServer.client(MockServer.key("ok"));
        Page<CatalogEntryBrief> page = client.catalog().validators().list();
        assertEquals(CatalogEntryBrief.TYPE_VALIDATOR, page.data().get(0).type());
        assertEquals("inn@1", page.data().get(0).validator().ref());
        CatalogEntry inn = client.catalog().validators().get("inn@1");
        assertEquals(5, inn.version().validator().tests().size());
        assertEquals("10 или 12 цифр", inn.version().validator().rulePreview().get(0));
        assertEquals("acme/contract-no", client.catalog().validators().get("acme/contract-no").ref());
        Validator mine = client.catalog().validators().fork("inn", null, "my-inn", "Мой ИНН", RequestOptions.NONE);
        assertEquals("my/my-inn", mine.ref());
        assertEquals("Мой ИНН", mine.name());
        // The schema catalog is unchanged: no type, schema entries.
        assertEquals(CatalogEntryBrief.TYPE_SCHEMA, client.catalog().list().data().get(0).type());
    }

    @Test
    @DisplayName("Validator models keep unknown fields and unknown enum values")
    void validatorModelsAreOpen() {
        ValidatorUpdateResult u = new ValidatorUpdateResult(Json.parseObject("{\"object\":\"validator_update\",\"bump\":\"giant\","
                + "\"validator\":{\"id\":\"val_1\",\"ref\":\"my/x\",\"shiny\":1,\"version\":{\"version\":\"2.0.0\","
                + "\"spec\":{\"kind\":\"future\",\"x\":[1]},\"tests\":[{\"value\":5,\"expect\":\"maybe\",\"why\":\"n\"}],"
                + "\"new_field\":true}},\"reasons\":[],\"propagated_to\":[{\"typizer_id\":\"tz_1\",\"version\":3}],"
                + "\"suggested_to\":[\"tz_2\"],\"later\":\"x\"}"));
        assertEquals(ValidatorBump.UNKNOWN, u.bump());
        assertEquals("giant", u.bumpValue());
        assertEquals("x", u.extra().get("later"));
        assertEquals(1L, ((Number) u.validator().extra().get("shiny")).longValue());
        ValidatorVersion v = u.validator().version();
        assertEquals(Boolean.TRUE, v.extra().get("new_field"));
        assertEquals("future", v.spec().get("kind"));
        assertEquals(5L, ((Number) v.tests().get(0).value()).longValue());
        assertEquals("maybe", v.tests().get(0).expect());
        assertEquals("n", v.tests().get(0).extra().get("why"));
        assertEquals("tz_1", u.propagatedTo().get(0).typizerId());
        assertEquals(Integer.valueOf(3), u.propagatedTo().get(0).version());
        assertEquals(Collections.singletonList("tz_2"), u.suggestedTo());

        FieldCheck fc = new FieldCheck(Json.parseObject("{\"ref\":\"doc:abc\",\"status\":\"maybe\",\"weight\":2}"));
        assertEquals(CheckStatus.UNKNOWN, fc.status());
        assertEquals("maybe", fc.statusValue());
        assertEquals(2L, ((Number) fc.extra().get("weight")).longValue());
        RunCheckUsed rc = new RunCheckUsed(Json.parseObject("{\"ref\":\"inline\",\"version\":null,\"name\":null,\"field\":\"a\",\"x\":1}"));
        assertNull(rc.version());
        assertTrue(rc.extra().containsKey("x"));

        ValidatorDetail d = new ValidatorDetail(Json.parseObject("{\"id\":\"val_1\",\"versions\":[],\"used_by\":"
                + "[{\"typizer_id\":\"tz_1\",\"update_available\":null,\"extra\":true}]}"));
        assertInstanceOf(Validator.class, d);
        assertNull(d.usedBy().get(0).updateAvailable());
        assertTrue(d.usedBy().get(0).extra().containsKey("extra"));
        assertTrue(new Validator(Json.parseObject("{}")).toString().startsWith("Validator{"));
        // Test values may come from documents: never in toString().
        assertFalse(v.toString().contains("5"));
        assertFalse(ValidatorTest.pass("ДГ-2026/0153").toString().contains("ДГ"));
        assertThrows(IllegalArgumentException.class, () -> ValidatorTest.pass(new Object()));
    }

    @Test
    @DisplayName("SC-13 extract waits for a slow run")
    void sc13_extractWaitsForASlowRun() {
        String key = MockServer.key("slow");
        Run run = MockServer.client(key).extract(byUrl().pollInterval(Duration.ofMillis(100)).build());
        assertEquals(RunStatus.SUCCEEDED, run.status());
        List<Map<String, Object>> log = MockServer.requests(key);
        assertEquals(4, log.size());
        assertEquals("extract", log.get(0).get("operation_id"));
        assertTrue(Integer.parseInt((String) map(log.get(0).get("query")).get("wait")) <= 60);
        for (int i = 1; i < 4; i++) {
            assertEquals("getRun", log.get(i).get("operation_id"));
            assertEquals("/v1/runs/" + run.id(), log.get(i).get("path"));
        }
    }

    @Test
    @DisplayName("SC-14 wait timeout")
    void sc14_waitTimeout() {
        String key = MockServer.key("slow");
        TypizerClient client = MockServer.client(key);
        long t0 = System.nanoTime();
        WaitTimeoutException e = assertThrows(WaitTimeoutException.class,
                () -> client.extract(byUrl().timeout(Duration.ofMillis(1500)).build()));
        assertTrue(System.nanoTime() - t0 < TimeUnit.SECONDS.toNanos(4));
        assertNotNull(e.runId());
        assertTrue(e.getMessage().contains("will be billed"));
        assertTrue(e.getMessage().contains("waitFor"));
        assertEquals("1", map(MockServer.requests(key).get(0).get("query")).get("wait"));
        // Not cancelled: it can still be waited for.
        assertEquals(RunStatus.SUCCEEDED, client.runs().waitFor(e.runId(), Duration.ofSeconds(30), Duration.ofMillis(100)).status());
    }

    // ---------------------------------------------------------------- errors

    @Test
    @DisplayName("SC-15 unauthorized")
    void sc15_unauthorized() {
        String key = MockServer.key("unauthorized");
        Lines lines = new Lines();
        TypizerClient client = TypizerClient.builder().apiKey(key).baseUrl(MockServer.baseUrl()).logger(new TypizerLogger() {
            @Override
            public void log(Level level, String message) {
                lines.log(level, message);
            }
        }).build();
        AuthenticationException e = assertThrows(AuthenticationException.class, client::me);
        assertEquals(401, e.statusCode());
        assertEquals("unauthorized", e.code());
        assertNotNull(e.requestId());
        assertEquals(e.headers().get("X-Request-Id").get(0), e.requestId());
        assertFalse(e.isRetryable());
        assertEquals(1, MockServer.requests(key).size());
        assertFalse(e.getMessage().contains(key));
        assertFalse(client.toString().contains(key));
        assertTrue(client.toString().contains("tz_test_…"));
        assertFalse(lines.all.isEmpty(), "DEBUG lines were written");
        for (String line : lines.all) assertFalse(line.contains(key), line);
        assertTrue(e.getMessage().matches("^\\[unauthorized] .+ \\(HTTP 401, request_id=\\S+, https://docs\\.typizer\\.ru/errors/unauthorized/?\\)$"),
                e.getMessage());

        // The same for a POST with a JSON body and with a file (HttpURLConnection treats 401 specially).
        AuthenticationException post = assertThrows(AuthenticationException.class, () -> client.typizers().create("x", schema()));
        assertNotNull(post.requestId());
        AuthenticationException upload = assertThrows(AuthenticationException.class,
                () -> client.files().upload(TypizerFile.fromBytes(PDF, "invoice.pdf")));
        assertEquals("unauthorized", upload.code());
        assertEquals(3, MockServer.requests(key).size());
    }

    @Test
    @DisplayName("SC-16 insufficient scope")
    void sc16_insufficientScope() {
        TypizerClient client = MockServer.client(MockServer.key("insufficient_scope"));
        assertNotNull(client.me().apiKey());
        PermissionDeniedException e = assertThrows(PermissionDeniedException.class, client::balance);
        assertEquals("insufficient_scope", e.code());
        assertEquals(403, e.statusCode());
    }

    @Test
    @DisplayName("SC-17 insufficient balance")
    void sc17_insufficientBalance() {
        String key = MockServer.key("insufficient_balance");
        TypizerClient client = MockServer.client(key);
        InsufficientBalanceException e = assertThrows(InsufficientBalanceException.class, () -> client.extract(byUrl().build()));
        assertEquals(402, e.statusCode());
        assertEquals("insufficient_balance", e.code());
        assertFalse(e.isRetryable());
        assertEquals(1, MockServer.requests(key).size());
        assertEquals(0L, client.balance().pagesAvailable().longValue());
    }

    @Test
    @DisplayName("SC-18 file too large")
    void sc18_fileTooLarge(@TempDir Path dir) throws IOException {
        String key = MockServer.key("file_too_large");
        TypizerClient client = MockServer.client(key);
        FileTooLargeException e = assertThrows(FileTooLargeException.class,
                () -> client.files().upload(TypizerFile.fromBytes(PDF, "invoice.pdf")));
        assertEquals(413, e.statusCode());
        assertEquals("file_too_large", e.code());
        assertEquals(1, MockServer.requests(key).size());

        // Over 50 MiB: rejected before sending.
        Path big = dir.resolve("big.pdf");
        try (RandomAccessFile f = new RandomAccessFile(big.toFile(), "rw")) {
            f.setLength(TypizerFile.MAX_BYTES + 1);
        }
        String okKey = MockServer.key("ok");
        TypizerClient ok = MockServer.client(okKey);
        FileTooLargeException local = assertThrows(FileTooLargeException.class, () -> ok.extract(big, schema()));
        assertEquals(413, local.statusCode());
        assertThrows(FileTooLargeException.class, () -> ok.files().upload(big));
        assertTrue(MockServer.requests(okKey).isEmpty());
    }

    @Test
    @DisplayName("SC-19 rate limited is retried with the same key")
    void sc19_rateLimitedIsRetriedWithTheSameKey() {
        String key = MockServer.key("rate_limited");
        TypizerClient client = MockServer.client(key);
        long t0 = System.nanoTime();
        client.typizers().create("Счета", schema());
        assertTrue(System.nanoTime() - t0 >= TimeUnit.MILLISECONDS.toNanos(1000), "waited Retry-After: 1");
        List<Map<String, Object>> log = MockServer.requests(key, "createTypizer");
        assertEquals(2, log.size());
        assertEquals(header(log.get(0), "idempotency-key"), header(log.get(1), "idempotency-key"));
        assertEquals(429, ((Number) map(log.get(0).get("response")).get("status")).intValue());
        assertEquals(201, ((Number) map(log.get(1).get("response")).get("status")).intValue());

        client.me();
        assertEquals(2, MockServer.requests(key, "getMe").size());
    }

    @Test
    @DisplayName("SC-20 rate limited without retries")
    void sc20_rateLimitedWithoutRetries() {
        String key = MockServer.key("rate_limited");
        TypizerClient client = MockServer.client(key).withMaxRetries(0);
        RateLimitException e = assertThrows(RateLimitException.class, client::me);
        assertEquals(Duration.ofSeconds(1), e.retryAfter());
        assertTrue(e.isRetryable());
        assertEquals(429, e.statusCode());
        assertEquals(0L, e.rateLimit().remaining().longValue());
        assertEquals(1, MockServer.requests(key).size());
    }

    @Test
    @DisplayName("SC-21 rate limited plain text body")
    void sc21_rateLimitedPlainTextBody() {
        String key = MockServer.key("rate_limited_plain");
        MockServer.client(key).me();
        assertEquals(2, MockServer.requests(key).size());

        String key2 = MockServer.key("rate_limited_plain");
        RateLimitException e = assertThrows(RateLimitException.class,
                () -> MockServer.client(key2).me(RequestOptions.builder().maxRetries(0).build()));
        assertEquals("rate_limited", e.code());
        assertNull(e.requestId());
        assertNull(e.problem());
        assertEquals("Too Many Requests", e.detail());
        assertEquals(Duration.ofSeconds(1), e.retryAfter());
    }

    @Test
    @DisplayName("SC-22 unavailable gives up after retries")
    void sc22_unavailableGivesUpAfterRetries() {
        String key = MockServer.key("unavailable");
        ServiceUnavailableException e = assertThrows(ServiceUnavailableException.class, () -> MockServer.client(key).me());
        assertEquals(503, e.statusCode());
        assertEquals("upstream_unavailable", e.code());
        assertTrue(e.isRetryable());
        assertEquals(3, MockServer.requests(key).size());
    }

    @Test
    @DisplayName("SC-23 unavailable once")
    void sc23_unavailableOnce() {
        String key = MockServer.key("unavailable_once");
        Run run = MockServer.client(key).extract(byUrl().build());
        assertEquals(RunStatus.SUCCEEDED, run.status());
        List<Map<String, Object>> log = MockServer.requests(key, "extract");
        assertEquals(2, log.size());
        assertEquals(header(log.get(0), "idempotency-key"), header(log.get(1), "idempotency-key"));
    }

    @Test
    @DisplayName("SC-24 internal error once retried with backoff")
    void sc24_internalErrorOnceRetriedWithBackoff() {
        String key = MockServer.key("internal_error_once");
        long t0 = System.nanoTime();
        MockServer.client(key).me();
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        assertTrue(ms >= 375, "backoff 0.5 s × [0.75, 1.0], was " + ms + " ms");
        assertEquals(2, MockServer.requests(key).size());
    }

    @Test
    @DisplayName("SC-25 dropped response is replayed")
    void sc25_droppedResponseIsReplayed() {
        String key = MockServer.key("drop_first_response");
        TypizerClient client = MockServer.client(key);
        Run run = client.extract(byUrl().build());
        assertEquals(RunStatus.SUCCEEDED, run.status());
        List<Map<String, Object>> log = MockServer.requests(key, "extract");
        assertEquals(2, log.size());
        assertEquals(header(log.get(0), "idempotency-key"), header(log.get(1), "idempotency-key"));
        assertEquals(Boolean.TRUE, map(log.get(0).get("response")).get("dropped"));
        assertEquals(Boolean.TRUE, map(log.get(1).get("response")).get("idempotent_replayed"));
        int runs = 0;
        for (Run ignored : client.runs().list(RunListParams.builder().limit(100).build()).autoPager()) runs++;
        assertEquals(26, runs);
    }

    @Test
    @DisplayName("SC-26 attempt timeout")
    void sc26_attemptTimeout() {
        String key = MockServer.key("slow_response");
        TypizerClient client = TypizerClient.builder().apiKey(key).baseUrl(MockServer.baseUrl())
                .timeout(Duration.ofMillis(500)).maxRetries(0).build();
        long t0 = System.nanoTime();
        TimeoutException e = assertThrows(TimeoutException.class, client::me);
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        assertInstanceOf(ConnectionException.class, e);
        assertTrue(e.isRetryable());
        assertTrue(ms >= 400 && ms < 2500, "about 0.5 s, was " + ms + " ms");
    }

    @Test
    @DisplayName("SC-27 maintenance warns once per client")
    void sc27_maintenanceWarnsOncePerClient() {
        Lines lines = new Lines();
        String key = MockServer.key("maintenance");
        TypizerClient client = TypizerClient.builder().apiKey(key).baseUrl(MockServer.baseUrl()).logger(lines).build();
        client.me();
        client.balance();
        TypizerResponse<Me> raw = client.withRawResponse(TypizerClient::me);
        assertTrue(raw.maintenance().startsWith("mnt_"));
        assertEquals(1, lines.warn.size(), lines.warn.toString());
        assertTrue(lines.warn.get(0).contains("mnt_"));
        assertEquals(1, client.status().data().size());
    }

    // ---------------------------------------------------------------- without a scenario

    @Test
    @DisplayName("SC-30 idempotency conflict")
    void sc30_idempotencyConflict() {
        String key = MockServer.key("ok");
        TypizerClient client = MockServer.client(key);
        RequestOptions same = RequestOptions.builder().idempotencyKey("same-key-" + key).build();
        client.typizers().create(TypizerParams.builder().name("Первый").schema(schema()).build(), same);
        IdempotencyConflictException e = assertThrows(IdempotencyConflictException.class,
                () -> client.typizers().create(TypizerParams.builder().name("Второй").schema(schema()).build(), same));
        assertEquals(409, e.statusCode());
        assertEquals("idempotency_conflict", e.code());
        assertFalse(e.isRetryable());
        assertEquals(2, MockServer.requests(key).size());
    }

    @Test
    @DisplayName("SC-31 argument errors before the network and schema errors from the server")
    void sc31_argumentErrorsBeforeTheNetworkAndSchemaErrorsFromTheServer() {
        String key = MockServer.key("ok");
        TypizerClient client = MockServer.client(key);
        assertThrows(IllegalArgumentException.class, () -> client.extract(ExtractParams.builder().url(SAMPLE_URL).build()));
        assertThrows(IllegalArgumentException.class,
                () -> client.extract(ExtractParams.builder().url(SAMPLE_URL).schema(schema()).catalog("upd").build()));
        assertThrows(IllegalArgumentException.class, () -> client.extract(TypizerFile.fromUrl(SAMPLE_URL), "schet-faktura"));
        assertThrows(IllegalArgumentException.class,
                () -> client.extract(ExtractParams.builder().url(SAMPLE_URL).catalog("upd").instructions("x").build()));
        assertThrows(IllegalArgumentException.class, () -> RequestOptions.builder().idempotencyKey("ключ"));
        assertTrue(MockServer.requests(key).isEmpty());

        BadRequestException e = assertThrows(BadRequestException.class,
                () -> client.extract(TypizerFile.fromUrl(SAMPLE_URL), "{\"type\": \"string\"}"));
        assertEquals("invalid_schema", e.code());
        assertEquals("schema", e.param());
        assertEquals(400, e.statusCode());
    }

    @Test
    @DisplayName("SC-33 custom transport and proxy")
    void sc33_customTransportAndProxy() throws Exception {
        String key = MockServer.key("ok");
        AtomicInteger calls = new AtomicInteger();
        UrlConnectionTransport inner = new UrlConnectionTransport();
        HttpTransport mine = new HttpTransport() {
            @Override
            public HttpResponse execute(HttpRequest request) throws IOException {
                calls.incrementAndGet();
                return inner.execute(request);
            }
        };
        TypizerClient client = TypizerClient.builder().apiKey(key).baseUrl(MockServer.baseUrl()).httpClient(mine).build();
        client.me();
        assertEquals(1, calls.get());
        assertEquals(1, MockServer.requests(key, "getMe").size());

        // An HTTP proxy with a login: the request goes through it and still reaches the mock.
        try (ForwardProxy proxy = new ForwardProxy()) {
            String key2 = MockServer.key("ok");
            TypizerClient viaProxy = TypizerClient.builder().apiKey(key2).baseUrl(MockServer.baseUrl())
                    .proxy("http://user:p%40ss@127.0.0.1:" + proxy.port()).build();
            assertEquals("api_key", viaProxy.me().auth());
            assertEquals(1, MockServer.requests(key2, "getMe").size());
            assertEquals(1, proxy.requestLines.size());
            assertTrue(proxy.requestLines.get(0).startsWith("GET http://"), proxy.requestLines.get(0));
            assertEquals("Basic " + java.util.Base64.getEncoder().encodeToString("user:p@ss".getBytes(StandardCharsets.UTF_8)),
                    proxy.proxyAuthorization.get(0));
        }
    }

    @Test
    @DisplayName("SC-34 cancel stops waiting without more requests")
    void sc34_cancelStopsWaitingWithoutMoreRequests() throws Exception {
        String key = MockServer.key("slow");
        TypizerClient client = MockServer.client(key);
        CompletableFuture<Run> future = client.async().extract(byUrl().pollInterval(Duration.ofSeconds(1)).build());
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (MockServer.requests(key).isEmpty() && System.nanoTime() < until) Thread.sleep(20);
        Thread.sleep(200); // the POST is answered; the client sleeps before its first poll
        assertTrue(future.cancel(true));
        assertTrue(future.isCancelled());
        assertThrows(CancellationException.class, future::get);
        Thread.sleep(1800);
        List<Map<String, Object>> log = MockServer.requests(key);
        assertEquals(1, log.size(), "no request after the cancel");

        // A cancel also aborts an HTTP attempt in flight.
        String key2 = MockServer.key("slow_response");
        long t0 = System.nanoTime();
        CompletableFuture<Me> me = MockServer.client(key2).async().call(TypizerClient::me);
        Thread.sleep(300);
        me.cancel(true);
        assertThrows(CancellationException.class, me::get);
        assertTrue(System.nanoTime() - t0 < TimeUnit.SECONDS.toNanos(2));
        try {
            me.get(1, TimeUnit.SECONDS);
        } catch (CancellationException | ExecutionException expected) {
            // cancelled
        }
    }

    /** A minimal HTTP forward proxy: one request per connection, recorded and relayed. */
    static final class ForwardProxy implements AutoCloseable {
        final ServerSocket server = new ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress());
        final List<String> requestLines = new CopyOnWriteArrayList<>();
        final List<String> proxyAuthorization = new CopyOnWriteArrayList<>();
        private final Thread thread;

        ForwardProxy() throws IOException {
            thread = new Thread(this::serve, "test-proxy");
            thread.setDaemon(true);
            thread.start();
        }

        int port() {
            return server.getLocalPort();
        }

        private void serve() {
            while (!server.isClosed()) {
                try (Socket client = server.accept()) {
                    InputStream in = client.getInputStream();
                    String head = readHead(in);
                    String[] lines = head.split("\r\n");
                    requestLines.add(lines[0]);
                    StringBuilder forwarded = new StringBuilder();
                    String[] first = lines[0].split(" ");
                    URI target = URI.create(first[1]);
                    forwarded.append(first[0]).append(' ').append(target.getRawPath())
                            .append(target.getRawQuery() != null ? "?" + target.getRawQuery() : "").append(" HTTP/1.1\r\n");
                    for (int i = 1; i < lines.length; i++) {
                        String l = lines[i].toLowerCase(java.util.Locale.ROOT);
                        if (l.startsWith("proxy-authorization:")) {
                            proxyAuthorization.add(lines[i].substring(lines[i].indexOf(':') + 1).trim());
                            continue;
                        }
                        if (l.startsWith("proxy-connection:") || l.startsWith("connection:")) continue;
                        forwarded.append(lines[i]).append("\r\n");
                    }
                    forwarded.append("Connection: close\r\n\r\n");
                    try (Socket upstream = new Socket()) {
                        upstream.connect(new InetSocketAddress(target.getHost(), target.getPort()));
                        upstream.getOutputStream().write(forwarded.toString().getBytes(StandardCharsets.ISO_8859_1));
                        upstream.getOutputStream().flush();
                        relay(upstream.getInputStream(), client.getOutputStream());
                    }
                } catch (IOException e) {
                    if (server.isClosed()) return;
                }
            }
        }

        private static String readHead(InputStream in) throws IOException {
            StringBuilder sb = new StringBuilder();
            for (int c; (c = in.read()) != -1; ) {
                sb.append((char) c);
                if (sb.toString().endsWith("\r\n\r\n")) break;
            }
            return sb.substring(0, Math.max(0, sb.length() - 4));
        }

        private static void relay(InputStream from, OutputStream to) throws IOException {
            byte[] buf = new byte[8192];
            for (int n; (n = from.read(buf)) != -1; ) to.write(buf, 0, n);
            to.flush();
        }

        @Override
        public void close() throws IOException {
            server.close();
        }
    }

    @Test
    void apiExceptionMessageFormat() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("x-request-id", Collections.singletonList("req_hdr"));
        ApiException e = ApiException.from(402, headers, "{\"type\":\"https://docs.typizer.ru/errors/insufficient_balance\","
                + "\"title\":\"Insufficient balance\",\"status\":402,\"detail\":\"Not enough pages on the balance: 1 needed, 0 available\","
                + "\"code\":\"insufficient_balance\",\"request_id\":\"req_01j\",\"doc_url\":\"https://docs.typizer.ru/errors/insufficient_balance\"}");
        assertInstanceOf(InsufficientBalanceException.class, e);
        assertEquals("[insufficient_balance] Not enough pages on the balance: 1 needed, 0 available (HTTP 402, "
                + "request_id=req_01j, https://docs.typizer.ru/errors/insufficient_balance)", e.getMessage());
    }
}
