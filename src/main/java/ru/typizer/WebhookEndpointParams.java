package ru.typizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ru.typizer.models.WebhookEventType;

/**
 * Fields of a webhook endpoint for {@code webhookEndpoints().create(…)} (url and events required) and
 * {@code webhookEndpoints().update(id, …)} (only what you set changes).
 */
public final class WebhookEndpointParams {
    final Map<String, Object> body;

    private WebhookEndpointParams(Map<String, Object> body) {
        this.body = body;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, Object> b = new LinkedHashMap<>();

        private Builder() {}

        /** Your HTTPS endpoint. */
        public Builder url(String url) {
            b.put("url", url);
            return this;
        }

        public Builder events(WebhookEventType... events) {
            List<String> v = new ArrayList<>();
            for (WebhookEventType e : events) v.add(ExtractParams.enumValue(e, "event"));
            b.put("events", v);
            return this;
        }

        /** {@code run.succeeded}, {@code run.failed}, {@code batch.completed}, {@code balance.low}. */
        public Builder events(String... events) {
            b.put("events", new ArrayList<>(Arrays.asList(events)));
            return this;
        }

        /** {@code active} or {@code disabled} (update only). */
        public Builder status(String status) {
            b.put("status", status);
            return this;
        }

        public WebhookEndpointParams build() {
            return new WebhookEndpointParams(new LinkedHashMap<>(b));
        }
    }
}
