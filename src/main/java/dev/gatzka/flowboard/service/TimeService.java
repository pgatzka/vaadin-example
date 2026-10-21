package dev.gatzka.flowboard.service;

import dev.gatzka.flowboard.domain.Card;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.RunningTimer;
import dev.gatzka.flowboard.domain.TimerSnapshot;
import dev.gatzka.flowboard.domain.WorkEntry;
import dev.gatzka.flowboard.domain.WorkEntrySnapshot;
import dev.gatzka.flowboard.repository.CardRepository;
import dev.gatzka.flowboard.repository.RunningTimerRepository;
import dev.gatzka.flowboard.repository.WorkEntryRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Time spent on cards: log it by hand or run a timer. Each person has at most one timer; starting
 * another stops and logs the first.
 */
@Service
@Transactional
public class TimeService {

    public static final int MAX_MINUTES = 24 * 60;
    public static final int MAX_NOTE_LENGTH = 200;

    private final CardRepository cards;
    private final WorkEntryRepository entries;
    private final RunningTimerRepository timers;
    private final ActivityLog activityLog;
    private final Clock clock;

    TimeService(CardRepository cards, WorkEntryRepository entries, RunningTimerRepository timers, ActivityLog activityLog, Clock clock) {
        this.cards = cards;
        this.entries = entries;
        this.timers = timers;
        this.activityLog = activityLog;
        this.clock = clock;
    }

    public CardSnapshot log(long cardId, String person, int minutes, String note, String actor) {
        if (minutes < 1 || minutes > MAX_MINUTES) {
            throw new IllegalArgumentException("Log between 1 minute and 24 hours at a time");
        }
        String cleanedNote = note == null || note.isBlank() ? null : note.strip();
        if (cleanedNote != null && cleanedNote.length() > MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException("Keep the note under " + MAX_NOTE_LENGTH + " characters");
        }
        Card card = requireCard(cardId);
        Instant now = clock.instant();
        WorkEntry entry = card.logWork(person, minutes, cleanedNote, now);
        entries.saveAndFlush(entry);
        activityLog.record(BoardChangedEvent.content(boardOf(card), actor,
                "logged %s on \"%s\"".formatted(humanize(minutes), card.getTitle())).forCard(cardId));
        return CardSnapshot.of(card);
    }

    public CardSnapshot removeEntry(long entryId, String actor) {
        WorkEntry entry = entries.findById(entryId).orElseThrow(() -> new NotFoundException("Work entry", entryId));
        Card card = entry.getCard();
        card.removeWork(entry, clock.instant());
        activityLog.record(BoardChangedEvent.content(boardOf(card), actor,
                "removed %s logged on \"%s\"".formatted(humanize(entry.getMinutes()), card.getTitle())).forCard(card.getId()));
        return CardSnapshot.of(card);
    }

    /** Starts a timer for the person on the card, first stopping and logging any timer they had running. */
    public TimerSnapshot start(long cardId, String person) {
        stop(person, null);
        Card card = requireCard(cardId);
        RunningTimer timer = timers.save(new RunningTimer(person, card, clock.instant()));
        activityLog.announce(BoardChangedEvent.content(boardOf(card), person, "started a timer on \"%s\"".formatted(card.getTitle())).forCard(cardId));
        return TimerSnapshot.of(timer);
    }

    /** Stops the person's timer, if any, and logs the elapsed time rounded up to whole minutes. */
    public Optional<CardSnapshot> stop(String person, String note) {
        Optional<RunningTimer> running = timers.findByPerson(person);
        if (running.isEmpty()) {
            return Optional.empty();
        }
        RunningTimer timer = running.get();
        timers.delete(timer);
        timers.flush();
        long elapsed = Duration.between(timer.getStartedAt(), clock.instant()).toSeconds();
        int minutes = (int) Math.min(MAX_MINUTES, Math.max(1, (elapsed + 59) / 60));
        return Optional.of(log(timer.getCard().getId(), person, minutes, note, person));
    }

    @Transactional(readOnly = true)
    public Optional<TimerSnapshot> running(String person) {
        return person == null ? Optional.empty() : timers.findByPerson(person).map(TimerSnapshot::of);
    }

    @Transactional(readOnly = true)
    public List<WorkEntrySnapshot> entries(long cardId) {
        return entries.findAllByCardIdOrderByLoggedAtDesc(cardId).stream().map(WorkEntrySnapshot::of).toList();
    }

    /** "45 min", "2 h" or "2 h 30 min". */
    public static String humanize(int minutes) {
        if (minutes < 60) {
            return minutes + " min";
        }
        int rest = minutes % 60;
        return rest == 0 ? (minutes / 60) + " h" : (minutes / 60) + " h " + rest + " min";
    }

    private Card requireCard(long cardId) {
        return cards.findById(cardId).orElseThrow(() -> new NotFoundException("Card", cardId));
    }

    private static long boardOf(Card card) {
        return card.getLane().getBoard().getId();
    }
}
