package ru.typizer;

import ru.typizer.models.Deleted;
import ru.typizer.models.WebhookDelivery;
import ru.typizer.models.WebhookEndpoint;
import ru.typizer.models.WebhookEventType;

/** Webhook endpoints: {@code client.webhookEndpoints()}. */
public final class WebhookEndpointService {
    private final Core core;
    private final Deliveries deliveries;

    WebhookEndpointService(Core core) {
        this.core = core;
        this.deliveries = new Deliveries(core);
    }

    /**
     * {@code POST /v1/webhook_endpoints}. The answer carries the signing {@code secret}
     * ({@code whsec_…}) — the only time it is shown (an idempotent replay of this call returns
     * {@code secret: null}).
     */
    public WebhookEndpoint create(String url, WebhookEventType... events) {
        return create(WebhookEndpointParams.builder().url(url).events(events).build(), RequestOptions.NONE);
    }

    public WebhookEndpoint create(WebhookEndpointParams params) {
        return create(params, RequestOptions.NONE);
    }

    public WebhookEndpoint create(WebhookEndpointParams params, RequestOptions options) {
        if (!params.body.containsKey("url") || !params.body.containsKey("events"))
            throw new IllegalArgumentException("A webhook endpoint needs url and events");
        return core.json(new Core.Call(Op.createWebhookEndpoint).jsonBody(params.body).options(options), WebhookEndpoint::new);
    }

    /** {@code GET /v1/webhook_endpoints/{id}}. */
    public WebhookEndpoint get(String id) {
        return get(id, RequestOptions.NONE);
    }

    public WebhookEndpoint get(String id, RequestOptions options) {
        return core.json(new Core.Call(Op.getWebhookEndpoint, id).options(options), WebhookEndpoint::new);
    }

    /** {@code PATCH /v1/webhook_endpoints/{id}}: url, events or {@code status} (re-enable a disabled endpoint). */
    public WebhookEndpoint update(String id, WebhookEndpointParams params) {
        return update(id, params, RequestOptions.NONE);
    }

    public WebhookEndpoint update(String id, WebhookEndpointParams params, RequestOptions options) {
        return core.json(new Core.Call(Op.updateWebhookEndpoint, id).jsonBody(params.body).options(options),
                WebhookEndpoint::new);
    }

    /** {@code DELETE /v1/webhook_endpoints/{id}}. */
    public Deleted delete(String id) {
        return delete(id, RequestOptions.NONE);
    }

    public Deleted delete(String id, RequestOptions options) {
        return core.json(new Core.Call(Op.deleteWebhookEndpoint, id).options(options), Deleted::new);
    }

    /** {@code GET /v1/webhook_endpoints}. */
    public Page<WebhookEndpoint> list() {
        return list(ListParams.none(), RequestOptions.NONE);
    }

    public Page<WebhookEndpoint> list(ListParams params, RequestOptions options) {
        return RunService.page(core, Op.listWebhookEndpoints, new String[0], TypizerService.listQuery(params),
                "starting_after", options, WebhookEndpoint::new);
    }

    /** {@code POST /v1/webhook_endpoints/{id}/test}: sends a test {@code run.succeeded} event. */
    public WebhookDelivery test(String id) {
        return test(id, RequestOptions.NONE);
    }

    public WebhookDelivery test(String id, RequestOptions options) {
        return core.json(new Core.Call(Op.testWebhookEndpoint, id).options(options), WebhookDelivery::new);
    }

    /** Deliveries of an endpoint. */
    public Deliveries deliveries() {
        return deliveries;
    }

    /** {@code client.webhookEndpoints().deliveries()}. */
    public static final class Deliveries {
        private final Core core;

        Deliveries(Core core) {
            this.core = core;
        }

        /** {@code GET /v1/webhook_endpoints/{id}/deliveries}. */
        public Page<WebhookDelivery> list(String endpointId) {
            return list(endpointId, ListParams.none(), RequestOptions.NONE);
        }

        public Page<WebhookDelivery> list(String endpointId, ListParams params, RequestOptions options) {
            return RunService.page(core, Op.listWebhookDeliveries, new String[] {endpointId},
                    TypizerService.listQuery(params), "starting_after", options, WebhookDelivery::new);
        }

        /** {@code POST /v1/webhook_deliveries/{id}/retry}: delivers the event again. */
        public WebhookDelivery retry(String deliveryId) {
            return retry(deliveryId, RequestOptions.NONE);
        }

        public WebhookDelivery retry(String deliveryId, RequestOptions options) {
            return core.json(new Core.Call(Op.retryWebhookDelivery, deliveryId).options(options), WebhookDelivery::new);
        }
    }
}
