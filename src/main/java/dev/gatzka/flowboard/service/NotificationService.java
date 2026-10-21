package dev.gatzka.flowboard.service;

import dev.gatzka.flowboard.domain.Notification;
import dev.gatzka.flowboard.domain.NotificationSnapshot;
import dev.gatzka.flowboard.repository.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Personal notifications. People are identified by display name, the same way assignees are;
 * nobody is ever notified about their own actions.
 */
@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notifications;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    NotificationService(NotificationRepository notifications, ApplicationEventPublisher events, Clock clock) {
        this.notifications = notifications;
        this.events = events;
        this.clock = clock;
    }

    /** Stores a notification unless the recipient is the actor; returns whether one was stored. */
    public boolean notify(String recipient, String actor, String message, long boardId, Long cardId) {
        if (recipient == null || recipient.isBlank() || Notification.key(recipient).equals(Notification.key(actor))) {
            return false;
        }
        Notification stored = notifications.save(new Notification(recipient, actor, message, boardId, cardId, clock.instant()));
        events.publishEvent(new InboxEvent(stored.getRecipient()));
        return true;
    }

    @Transactional(readOnly = true)
    public long unreadCount(String displayName) {
        return notifications.countByRecipientAndReadAtIsNull(Notification.key(displayName));
    }

    @Transactional(readOnly = true)
    public List<NotificationSnapshot> recent(String displayName) {
        return notifications.findTop30ByRecipientOrderByCreatedAtDesc(Notification.key(displayName)).stream()
                .map(NotificationSnapshot::of)
                .toList();
    }

    public void markRead(long notificationId) {
        notifications.findById(notificationId).ifPresent(notification -> notification.markRead(clock.instant()));
    }

    public void markAllRead(String displayName) {
        Instant now = clock.instant();
        notifications.findAllByRecipientAndReadAtIsNull(Notification.key(displayName)).forEach(n -> n.markRead(now));
    }

    void forgetBoard(long boardId) {
        notifications.deleteAllByBoardId(boardId);
    }
}
