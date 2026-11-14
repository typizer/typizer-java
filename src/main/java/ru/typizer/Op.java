package ru.typizer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Every operation of the public API ({@code apps/docs/openapi.public.json}): method, path, query
 * parameters, whether it takes {@code Idempotency-Key}. Constant names are the {@code operationId}s.
 * {@code ContractTest} checks this table against the OpenAPI document operation by operation, and
 * that the SDK calls each one.
 */
enum Op {
    getStatus("GET", "/v1/status"),
    getOpenApiDocument("GET", "/v1/openapi.json"),
    getMe("GET", "/v1/me"),
    getOrganization("GET", "/v1/organization"),
    createFile("POST", "/v1/files"),
    getFile("GET", "/v1/files/{id}"),
    deleteFile("DELETE", "/v1/files/{id}"),
    getFileContent("GET", "/v1/files/{id}/content"),
    listTypizers("GET", "/v1/typizers", "limit", "starting_after"),
    createTypizer("POST", "/v1/typizers"),
    generateTypizer("POST", "/v1/typizers/generate"),
    getTypizer("GET", "/v1/typizers/{id}"),
    updateTypizer("PATCH", "/v1/typizers/{id}"),
    deleteTypizer("DELETE", "/v1/typizers/{id}"),
    listTypizerVersions("GET", "/v1/typizers/{id}/versions", "limit", "starting_after"),
    getTypizerVersion("GET", "/v1/typizers/{id}/versions/{n}"),
    getTypizerXsd("GET", "/v1/typizers/{id}/xsd"),
    getCatalogPublisher("GET", "/v1/catalog/publishers/{slug}"),
    listCatalog("GET", "/v1/catalog", "q", "category", "kind", "publisher", "favorites", "sort", "limit", "cursor"),
    installCatalogEntry("POST", "/v1/catalog/{entry}/install"),
    forkCatalogEntry("POST", "/v1/catalog/{entry}/fork"),
    favoriteCatalogEntry("PUT", "/v1/catalog/{entry}/favorite"),
    unfavoriteCatalogEntry("DELETE", "/v1/catalog/{entry}/favorite"),
    listCatalogVersions("GET", "/v1/catalog/{entry}/versions"),
    reportCatalogEntry("POST", "/v1/catalog/{entry}/reports"),
    getCatalogEntry("GET", "/v1/catalog/{entry}", "version"),
    getCommunityCatalogEntry("GET", "/v1/catalog/{publisher}/{slug}", "version"),
    getTypizerCatalogUpdate("GET", "/v1/typizers/{id}/catalog_update", "version"),
    applyTypizerCatalogUpdate("POST", "/v1/typizers/{id}/catalog_update"),
    createRun("POST", "/v1/typizers/{id}/runs", "wait"),
    listRuns("GET", "/v1/runs", "limit", "starting_after", "status", "typizer_id", "batch_id", "source",
            "created_after", "created_before"),
    getRun("GET", "/v1/runs/{id}"),
    getRunOutput("GET", "/v1/runs/{id}/output", "format"),
    retryRun("POST", "/v1/runs/{id}/retry"),
    createBatch("POST", "/v1/batches"),
    listBatches("GET", "/v1/batches", "limit", "starting_after"),
    getBatch("GET", "/v1/batches/{id}"),
    exportBatch("GET", "/v1/batches/{id}/export", "format"),
    extract("POST", "/v1/extract", "wait"),
    listWebhookEndpoints("GET", "/v1/webhook_endpoints", "limit", "starting_after"),
    createWebhookEndpoint("POST", "/v1/webhook_endpoints"),
    getWebhookEndpoint("GET", "/v1/webhook_endpoints/{id}"),
    updateWebhookEndpoint("PATCH", "/v1/webhook_endpoints/{id}"),
    deleteWebhookEndpoint("DELETE", "/v1/webhook_endpoints/{id}"),
    testWebhookEndpoint("POST", "/v1/webhook_endpoints/{id}/test"),
    listWebhookDeliveries("GET", "/v1/webhook_endpoints/{id}/deliveries", "limit", "starting_after"),
    retryWebhookDelivery("POST", "/v1/webhook_deliveries/{id}/retry"),
    getBalance("GET", "/v1/balance"),
    getUsage("GET", "/v1/usage", "from", "to", "group_by");

    final String method;
    final String path;
    final List<String> query;

    Op(String method, String path, String... query) {
        this.method = method;
        this.path = path;
        this.query = Collections.unmodifiableList(Arrays.asList(query));
    }

    /** Every {@code POST} carries an {@code Idempotency-Key} (SPEC §7.3). */
    boolean sendsIdempotencyKey() {
        return "POST".equals(method);
    }
}
