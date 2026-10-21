package dev.gatzka.flowboard.webhook;

import dev.gatzka.flowboard.domain.Webhook;
import dev.gatzka.flowboard.domain.WebhookDelivery;
import dev.gatzka.flowboard.repository.WebhookDeliveryRepository;
import dev.gatzka.flowboard.repository.WebhookRepository;
import dev.gatzka.flowboard.service.ActivityLog;
import dev.gatzka.flowboard.service.BoardChangedEvent;
import dev.gatzka.flowboard.service.NotFoundException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Webhook registrations and their delivery log. Delivery itself is {@link WebhookDispatcher}'s job. */
@Service
@Transactional
public class WebhookService {

    static final int MAX_URL_LENGTH = 500;
    static final int MAX_SECRET_LENGTH = 100;
    static final int KEPT_DELIVERIES = 20;

    private final WebhookRepository webhooks;
    private final WebhookDeliveryRepository deliveries;
    private final ActivityLog activityLog;
    private final Clock clock;

    WebhookService(WebhookRepository webhooks, WebhookDeliveryRepository deliveries, ActivityLog activityLog, Clock clock) {
        this.webhooks = webhooks;
        this.deliveries = deliveries;
        this.activityLog = activityLog;
        this.clock = clock;
    }

    public WebhookSnapshot add(long boardId, String url, String secret, String actor) {
        String cleanedUrl = validUrl(url);
        String cleanedSecret = secret == null || secret.isBlank() ? null : secret.strip();
        if (cleanedSecret != null && cleanedSecret.length() > MAX_SECRET_LENGTH) {
            throw new IllegalArgumentException("Keep the secret under " + MAX_SECRET_LENGTH + " characters");
        }
        Webhook webhook = webhooks.save(new Webhook(boardId, cleanedUrl, cleanedSecret, clock.instant()));
        activityLog.record(BoardChangedEvent.updated(boardId, actor, "added a webhook for " + hostOf(cleanedUrl)));
        return snapshot(webhook);
    }

    public void remove(long webhookId, String actor) {
        Webhook webhook = require(webhookId);
        webhooks.delete(webhook);
        activityLog.record(BoardChangedEvent.updated(webhook.getBoardId(), actor, "removed the webhook for " + hostOf(webhook.getUrl())));
    }

    public WebhookSnapshot setEnabled(long webhookId, boolean enabled, String actor) {
        Webhook webhook = require(webhookId);
        if (webhook.isEnabled() != enabled) {
            webhook.setEnabled(enabled);
            activityLog.record(BoardChangedEvent.updated(webhook.getBoardId(), actor,
                    (enabled ? "enabled" : "paused") + " the webhook for " + hostOf(webhook.getUrl())));
        }
        return snapshot(webhook);
    }

    @Transactional(readOnly = true)
    public List<WebhookSnapshot> forBoard(long boardId) {
        return webhooks.findAllByBoardIdOrderByCreatedAtAsc(boardId).stream().map(this::snapshot).toList();
    }

    @Transactional(readOnly = true)
    public List<WebhookSnapshot.DeliverySnapshot> deliveries(long webhookId) {
        return deliveries.findAllByWebhookIdOrderByAttemptedAtDesc(webhookId).stream().map(WebhookSnapshot.DeliverySnapshot::of).toList();
    }

    /** Writes a delivery attempt in its own transaction and keeps only the latest few per webhook. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDelivery(long webhookId, String event, Integer statusCode, boolean succeeded, String error) {
        if (!webhooks.existsById(webhookId)) {
            return;
        }
        String cleanedError = error == null ? null : error.length() > 300 ? error.substring(0, 300) : error;
        deliveries.save(new WebhookDelivery(webhookId, event, statusCode, succeeded, cleanedError, clock.instant()));
        List<WebhookDelivery> all = deliveries.findAllByWebhookIdOrderByAttemptedAtDesc(webhookId);
        if (all.size() > KEPT_DELIVERIES) {
            deliveries.deleteAll(all.subList(KEPT_DELIVERIES, all.size()));
        }
    }

    public void forgetBoard(long boardId) {
        webhooks.deleteAllByBoardId(boardId);
    }

    static String validUrl(String url) {
        String cleaned = url == null ? "" : url.strip();
        if (cleaned.isEmpty() || cleaned.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException("Enter a URL of at most " + MAX_URL_LENGTH + " characters");
        }
        try {
            URI uri = new URI(cleaned);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null) {
                throw new IllegalArgumentException("Webhook URLs start with http:// or https:// and name a host");
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("That is not a valid URL");
        }
        return cleaned;
    }

    private static String hostOf(String url) {
        return URI.create(url).getHost();
    }

    private WebhookSnapshot snapshot(Webhook webhook) {
        return WebhookSnapshot.of(webhook, deliveries.findFirstByWebhookIdOrderByAttemptedAtDesc(webhook.getId()));
    }

    private Webhook require(long webhookId) {
        return webhooks.findById(webhookId).orElseThrow(() -> new NotFoundException("Webhook", webhookId));
    }
}
