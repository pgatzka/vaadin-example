package dev.gatzka.flowboard.webhook;

import dev.gatzka.flowboard.domain.Webhook;
import dev.gatzka.flowboard.domain.WebhookDelivery;
import java.time.Instant;
import java.util.Optional;

/** A webhook as shown in the UI, with its most recent delivery. */
public record WebhookSnapshot(long id, String url, boolean hasSecret, boolean enabled, Instant createdAt,
        Optional<DeliverySnapshot> lastDelivery) {

    public static WebhookSnapshot of(Webhook webhook, Optional<WebhookDelivery> lastDelivery) {
        return new WebhookSnapshot(webhook.getId(), webhook.getUrl(), webhook.getSecret() != null, webhook.isEnabled(),
                webhook.getCreatedAt(), lastDelivery.map(DeliverySnapshot::of));
    }

    public record DeliverySnapshot(String event, Integer statusCode, boolean succeeded, String error, Instant attemptedAt) {

        public static DeliverySnapshot of(WebhookDelivery delivery) {
            return new DeliverySnapshot(delivery.getEvent(), delivery.getStatusCode(), delivery.isSucceeded(),
                    delivery.getError(), delivery.getAttemptedAt());
        }

        public String summary() {
            if (succeeded) {
                return "HTTP " + statusCode;
            }
            return statusCode != null ? "HTTP " + statusCode : error == null ? "Failed" : error;
        }
    }
}
