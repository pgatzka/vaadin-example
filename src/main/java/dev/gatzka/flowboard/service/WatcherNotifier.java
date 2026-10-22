package dev.gatzka.flowboard.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns committed card changes into inbox notifications for the card's watchers. Only changes worth an interruption
 * count: comments, moves, edits, archiving, restoring and blockers; votes, checklist ticks and timers stay quiet.
 * Somebody who was just told about the same change by name (the assignee, a mentioned person) is not told twice.
 */
@Component
public class WatcherNotifier {

    static final List<String> NOTEWORTHY = List.of("commented on", "moved", "updated", "archived", "restored", "marked", "copied", "scheduled");
    private static final Duration DUPLICATE_WINDOW = Duration.ofSeconds(10);

    private final WatchService watches;
    private final NotificationService notifications;
    private final Clock clock;

    WatcherNotifier(WatchService watches, NotificationService notifications, Clock clock) {
        this.watches = watches;
        this.notifications = notifications;
        this.clock = clock;
    }

    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(BoardChangedEvent event) {
        if (event.cardId() == null || event.kind() != BoardChangedEvent.Kind.CONTENT || !isNoteworthy(event.message())) {
            return;
        }
        Instant recently = clock.instant().minus(DUPLICATE_WINDOW);
        for (String watcher : watches.watchers(event.cardId())) {
            if (notifications.notifiedSince(watcher, event.cardId(), event.actor(), recently)) {
                continue;
            }
            notifications.notify(watcher, event.actor(), event.message(), event.boardId(), event.cardId());
        }
    }

    static boolean isNoteworthy(String message) {
        return message != null && NOTEWORTHY.stream().anyMatch(message::startsWith);
    }
}
