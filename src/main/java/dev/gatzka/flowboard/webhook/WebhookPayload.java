package dev.gatzka.flowboard.webhook;

import java.time.Instant;

/**
 * The JSON body posted to a webhook.
 *
 * @param event one of {@code created}, {@code updated}, {@code deleted}, {@code content} or {@code test}
 */
public record WebhookPayload(String event, long boardId, String boardName, String actor, String message, Long cardId,
        Instant occurredAt) {
}
