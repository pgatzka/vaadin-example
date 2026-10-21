package dev.gatzka.flowboard.webhook;

import dev.gatzka.flowboard.domain.Webhook;
import dev.gatzka.flowboard.repository.BoardRepository;
import dev.gatzka.flowboard.repository.WebhookRepository;
import dev.gatzka.flowboard.service.BoardChangedEvent;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

/**
 * Posts board changes to the board's webhooks after the change is committed. Calls run on
 * virtual threads so a slow endpoint never holds up the user who made the change.
 */
@Component
public class WebhookDispatcher {

    static final String EVENT_HEADER = "X-Flowboard-Event";
    static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Logger LOG = LoggerFactory.getLogger(WebhookDispatcher.class);

    private final WebhookRepository webhooks;
    private final BoardRepository boards;
    private final WebhookService service;
    private final JsonMapper json;
    private final Executor executor;
    private final Clock clock;
    private final HttpClient http;

    WebhookDispatcher(WebhookRepository webhooks, BoardRepository boards, WebhookService service, JsonMapper json,
            Executor broadcastExecutor, Clock clock) {
        this.webhooks = webhooks;
        this.boards = boards;
        this.service = service;
        this.json = json;
        this.executor = broadcastExecutor;
        this.clock = clock;
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onBoardChanged(BoardChangedEvent event) {
        List<Webhook> targets = webhooks.findAllByBoardIdAndEnabledTrue(event.boardId());
        if (targets.isEmpty()) {
            return;
        }
        String boardName = boards.findById(event.boardId()).map(board -> board.getName()).orElse(null);
        WebhookPayload payload = new WebhookPayload(event.kind().name().toLowerCase(Locale.ROOT), event.boardId(), boardName,
                event.actor(), event.message(), event.cardId(), clock.instant());
        targets.forEach(webhook -> executor.execute(() -> deliver(webhook, payload)));
    }

    /** Sends a {@code test} event to one webhook right away, bypassing the event queue. */
    public void sendTest(long webhookId, String actor) {
        webhooks.findById(webhookId).ifPresent(webhook -> {
            String boardName = boards.findById(webhook.getBoardId()).map(board -> board.getName()).orElse(null);
            WebhookPayload payload = new WebhookPayload("test", webhook.getBoardId(), boardName, actor,
                    "sent a test event", null, clock.instant());
            executor.execute(() -> deliver(webhook, payload));
        });
    }

    void deliver(Webhook webhook, WebhookPayload payload) {
        byte[] body = json.writeValueAsBytes(payload);
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(webhook.getUrl()))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("User-Agent", "Flowboard-Webhook/1.0")
                .header(EVENT_HEADER, payload.event())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (webhook.getSecret() != null) {
            request.header(Signatures.HEADER, Signatures.sign(webhook.getSecret(), body));
        }
        try {
            HttpResponse<Void> response = http.send(request.build(), HttpResponse.BodyHandlers.discarding());
            boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
            service.recordDelivery(webhook.getId(), payload.event(), response.statusCode(), ok, ok ? null : "Unexpected status");
        } catch (IOException | RuntimeException e) {
            LOG.debug("Webhook {} failed", webhook.getUrl(), e);
            service.recordDelivery(webhook.getId(), payload.event(), null, false, e.getClass().getSimpleName() + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
