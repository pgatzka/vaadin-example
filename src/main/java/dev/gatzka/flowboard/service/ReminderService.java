package dev.gatzka.flowboard.service;

import dev.gatzka.flowboard.domain.Card;
import dev.gatzka.flowboard.repository.CardRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every morning, tells people about their cards that are due today or overdue, once per card and
 * day, through the inbox.
 */
@Service
@Transactional
public class ReminderService {

    private static final Logger LOG = LoggerFactory.getLogger(ReminderService.class);

    private final CardRepository cards;
    private final NotificationService notifications;
    private final Clock clock;

    ReminderService(CardRepository cards, NotificationService notifications, Clock clock) {
        this.cards = cards;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 7 * * *")
    public void remindEveryone() {
        int sent = remind(LocalDate.now(clock));
        if (sent > 0) {
            LOG.info("Sent {} due-date reminders", sent);
        }
    }

    /** Sends the reminders for the given day and returns how many were sent; already sent ones are skipped. */
    public int remind(LocalDate today) {
        Instant startOfDay = today.atStartOfDay(clock.getZone()).toInstant();
        int sent = 0;
        for (Card card : cards.findAllByArchivedAtIsNull()) {
            if (card.getAssignee() == null || card.getDueDate() == null || card.getLane().isDone() || card.getDueDate().isAfter(today)) {
                continue;
            }
            if (notifications.notifiedSince(card.getAssignee(), card.getId(), ActivityLog.SYSTEM_ACTOR, startOfDay)) {
                continue;
            }
            long daysLate = ChronoUnit.DAYS.between(card.getDueDate(), today);
            String message = daysLate == 0
                    ? "\"%s\" is due today".formatted(card.getTitle())
                    : "\"%s\" is %d %s overdue".formatted(card.getTitle(), daysLate, daysLate == 1 ? "day" : "days");
            if (notifications.notify(card.getAssignee(), ActivityLog.SYSTEM_ACTOR, message, card.getLane().getBoard().getId(), card.getId())) {
                sent++;
            }
        }
        return sent;
    }
}
