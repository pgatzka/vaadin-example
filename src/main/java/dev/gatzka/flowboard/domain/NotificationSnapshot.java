package dev.gatzka.flowboard.domain;

import java.time.Instant;

public record NotificationSnapshot(long id, String actor, String message, long boardId, Long cardId, Instant createdAt, boolean unread) {

    public static NotificationSnapshot of(Notification notification) {
        return new NotificationSnapshot(notification.getId(), notification.getActor(), notification.getMessage(),
                notification.getBoardId(), notification.getCardId(), notification.getCreatedAt(), notification.isUnread());
    }
}
