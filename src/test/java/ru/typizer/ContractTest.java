package ru.typizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import ru.typizer.internal.Json;
import ru.typizer.models.Batch;
import ru.typizer.models.CatalogUpdate;
import ru.typizer.models.Format;
import ru.typizer.models.Run;
import ru.typizer.models.RunStatus;
import ru.typizer.models.Typizer;
import ru.typizer.models.WebhookDelivery;
import ru.typizer.models.WebhookEndpoint;
import ru.typizer.models.WebhookEventType;

/**
 * The SDK against {@code apps/docs/openapi.public.json}, the only contract: the operation table
 * matches the document operation by operation, every operation is called by an SDK method (checked
 * in the mock's request log), and every field of every response schema has an accessor.
 */
class ContractTest {
    static Map<String, Object> openapi;

    @BeforeAll
    static void load() throws IOException {
        Path file = MockServer.find("apps/docs/openapi.public.json", "src/test/resources/openapi.public.json");
        assertNotNull(file, "openapi.public.json not found");
        openapi = Json.parseObject(new String(java.nio.file.Files.readAllBytes(file), StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> obj(Object o) {
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    static List<Object> list(Object o) {
        return o == null ? Collections.emptyList() : (List<Object>) o;
    }

    static Map<String, Object> resolve(Map<String, Object> node) {
        Object ref = node.get("$ref");
        if (!(ref instanceof String)) return node;
        Map<String, Object> cur = openapi;
        for (String part : ((String) ref).substring(2).split("/")) cur = obj(cur.get(part));
        return cur;
    }

    /** operationId → {method, path, query names, takes Idempotency-Key}. */
    static Map<String, Object[]> operations() {
        Map<String, Object[]> ops = new LinkedHashMap<>();
        for (Map.Entry<String, Object> p : obj(openapi.get("paths")).entrySet()) {
            for (Map.Entry<String, Object> m : obj(p.getValue()).entrySet()) {
                if (!(m.getValue() instanceof Map)) continue;
                Map<String, Object> op = obj(m.getValue());
                if (!op.containsKey("operationId")) continue;
                Set<String> query = new TreeSet<>();
                boolean idempotency = false;
                for (Object raw : list(op.get("parameters"))) {
                    Map<String, Object> param = resolve(obj(raw));
                    if ("query".equals(param.get("in"))) query.add((String) param.get("name"));
                    if ("header".equals(param.get("in")) && "Idempotency-Key".equals(param.get("name"))) idempotency = true;
                }
                ops.put((String) op.get("operationId"), new Object[] {m.getKey().toUpperCase(java.util.Locale.ROOT), p.getKey(), query, idempotency});
            }
        }
        return ops;
    }

    @Test
    void operationTableMatchesTheOpenApiDocument() {
        Map<String, Object[]> ops = operations();
        assertEquals(49, ops.size(), "public operations");
        assertEquals(new TreeSet<>(ops.keySet()), new TreeSet<>(toSet(Op.values())));
        for (Map.Entry<String, Object[]> e : ops.entrySet()) {
            Op op = Op.valueOf(e.getKey());
            Object[] spec = e.getValue();
            assertEquals(spec[0], op.method, e.getKey() + " method");
            assertEquals(spec[1], op.path, e.getKey() + " path");
            assertEquals(spec[2], new TreeSet<>(op.query), e.getKey() + " query parameters");
            // Every POST takes Idempotency-Key in the document, and the SDK sends one on each (SPEC §7.3).
            assertEquals(spec[3], op.sendsIdempotencyKey(), e.getKey() + " Idempotency-Key");
        }
    }

    private static Set<String> toSet(Op[] values) {
        Set<String> s = new HashSet<>();
        for (Op op : values) s.add(op.name());
        return s;
    }

    @Test
    void everyOperationIsCalledBySdkMethod() {
        String key = MockServer.key("ok");
        TypizerClient c = MockServer.client(key);
        Map<String, Object> schema = ConformanceTest.schema();

        c.status();
        c.openApiDocument();
        c.me();
        c.organization();
        c.balance();
        c.usage(UsageParams.builder().groupBy("typizer").build());

        ru.typizer.models.File file = c.files().upload(TypizerFile.fromBytes(ConformanceTest.PDF, "invoice.pdf"));
        assertEquals(file.id(), c.files().get(file.id()).id());
        assertEquals(ConformanceTest.PDF.length, c.files().content(file.id()).bytes().length);

        Typizer t = c.typizers().create("Счета", schema);
        c.typizers().list();
        c.typizers().get(t.id());
        c.typizers().update(t.id(), TypizerParams.builder().description("Входящие счета").build());
        c.typizers().generate("Счёт на оплату: номер, дата, сумма, ИНН продавца");
        c.typizers().versions().list(t.id());
        c.typizers().versions().get(t.id(), 1);
        assertTrue(c.typizers().xsd(t.id()).text().contains("xs:schema"));

        c.catalog().list();
        c.catalog().get("schet-faktura@1");
        c.catalog().get("acme/akt-sverki");
        c.catalog().versions("schet-faktura");
        c.catalog().publishers().get("acme");
        c.catalog().favorite("schet-faktura");
        c.catalog().unfavorite("schet-faktura");
        c.catalog().report("acme/akt-sverki", "spam");
        Typizer installed = c.catalog().install("schet-faktura");
        c.catalog().fork("upd");
        CatalogUpdate update = c.typizers().catalogUpdate().get(installed.id());
        c.typizers().catalogUpdate().apply(installed.id(), update.to());

        Run run = c.extract(ExtractParams.builder().url(ConformanceTest.SAMPLE_URL).schema(schema).build());
        Run typizerRun = c.runs().create(ExtractParams.builder().fileId(file.id()).typizer(t).build(), java.time.Duration.ofSeconds(60));
        c.runs().get(typizerRun.id());
        c.runs().list();
        assertTrue(c.runs().output(run.id(), Format.XML).text().startsWith("<"));
        Run failed = c.runs().list(RunListParams.builder().status(RunStatus.FAILED).build()).data().get(0);
        c.runs().retry(failed.id());

        Batch batch = c.batches().create(BatchParams.builder().typizer(t).fileId(file.id()).build());
        c.batches().get(batch.id());
        c.batches().list();
        c.batches().export(batch.id(), Format.CSV).bytes();

        WebhookEndpoint endpoint = c.webhookEndpoints().create("https://example.com/typizer", WebhookEventType.RUN_SUCCEEDED);
        c.webhookEndpoints().list();
        c.webhookEndpoints().get(endpoint.id());
        c.webhookEndpoints().update(endpoint.id(), WebhookEndpointParams.builder().events("run.succeeded", "run.failed").build());
        WebhookDelivery delivery = c.webhookEndpoints().test(endpoint.id());
        c.webhookEndpoints().deliveries().list(endpoint.id());
        c.webhookEndpoints().deliveries().retry(delivery.id());
        c.webhookEndpoints().delete(endpoint.id());

        c.files().delete(file.id());
        c.typizers().delete(t.id());

        Set<String> called = new TreeSet<>();
        for (Map<String, Object> r : MockServer.requests(key)) {
            int status = ((Number) MockServer.map(r.get("response")).get("status")).intValue();
            if (status >= 400) fail(r.get("operation_id") + " answered " + status);
            called.add((String) r.get("operation_id"));
        }
        assertEquals(new TreeSet<>(operations().keySet()), called);
    }

    /** Model class per response schema; schemas only used in requests or as plain lists are skipped. */
    static final Map<String, Class<?>> MODELS = new HashMap<>();
    static final Set<String> NOT_MODELS = new HashSet<>(Arrays.asList("Scope", "CatalogList", "WebhookEventType"));

    static {
        String[] names = {"Run", "File", "Typizer", "TypizerDraft", "TypizerVersion", "Batch", "WebhookEndpoint",
            "WebhookDelivery", "Balance", "BalanceLot", "Me", "Organization", "Usage", "UsageRow", "CatalogEntryBrief",
            "CatalogEntry", "CatalogVersionBrief", "CatalogVersion", "CatalogPublisher", "CatalogPublisherBrief",
            "CatalogFavorite", "CatalogReport", "CatalogUpdate", "SchemaChange", "Status", "MaintenanceEvent", "Problem"};
        try {
            for (String n : names) MODELS.put(n, Class.forName("ru.typizer.models." + n));
            for (String n : new String[] {"DeletedFile", "DeletedTypizer", "DeletedWebhookEndpoint"})
                MODELS.put(n, Class.forName("ru.typizer.models.Deleted"));
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void everyResponseFieldHasAnAccessor() throws Exception {
        Map<String, Object> schemas = obj(obj(openapi.get("components")).get("schemas"));
        List<String> missing = new ArrayList<>();
        for (String name : schemas.keySet()) {
            if (name.endsWith("Request") || NOT_MODELS.contains(name)) continue;
            Class<?> model = MODELS.get(name);
            if (model == null) {
                missing.add(name + " (no model class)");
                continue;
            }
            Field f = model.getDeclaredField("FIELDS");
            f.setAccessible(true);
            Set<String> known = (Set<String>) f.get(null);
            for (String prop : properties(obj(schemas.get(name)))) {
                if (!known.contains(prop)) missing.add(name + "." + prop);
            }
        }
        assertTrue(missing.isEmpty(), "Fields without an accessor: " + missing);
    }

    private static Set<String> properties(Map<String, Object> schema) {
        Set<String> out = new TreeSet<>();
        Map<String, Object> s = resolve(schema);
        if (s.get("properties") instanceof Map) out.addAll(obj(s.get("properties")).keySet());
        for (Object part : list(s.get("allOf"))) out.addAll(properties(obj(part)));
        return out;
    }
}
