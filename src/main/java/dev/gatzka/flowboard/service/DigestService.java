package dev.gatzka.flowboard.service;

import dev.gatzka.flowboard.domain.Activity;
import dev.gatzka.flowboard.domain.ActivityPulse;
import dev.gatzka.flowboard.domain.Board;
import dev.gatzka.flowboard.domain.Card;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.WeeklyDigest;
import dev.gatzka.flowboard.repository.ActivityRepository;
import dev.gatzka.flowboard.repository.BoardRepository;
import dev.gatzka.flowboard.repository.CardRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the weekly digest of a board. */
@Service
@Transactional(readOnly = true)
public class DigestService {

    private final BoardRepository boards;
    private final CardRepository cards;
    private final ActivityRepository activities;
    private final Clock clock;

    DigestService(BoardRepository boards, CardRepository cards, ActivityRepository activities, Clock clock) {
        this.boards = boards;
        this.cards = cards;
        this.activities = activities;
        this.clock = clock;
    }

    /** The digest of the seven days ending on {@code weekEnding} (inclusive). */
    public WeeklyDigest weekly(long boardId, LocalDate weekEnding) {
        Board board = boards.findById(boardId).orElseThrow(() -> new NotFoundException("Board", boardId));
        ZoneId zone = clock.getZone();
        LocalDate from = weekEnding.minusDays(6);
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = weekEnding.plusDays(1).atStartOfDay(zone).toInstant();

        List<CardSnapshot> completed = new ArrayList<>();
        List<CardSnapshot> created = new ArrayList<>();
        List<CardSnapshot> overdue = new ArrayList<>();
        List<CardSnapshot> blocked = new ArrayList<>();
        List<CardSnapshot> dueNextWeek = new ArrayList<>();
        for (Card card : cards.findAllByLaneBoardIdAndArchivedAtIsNullOrderByPositionAsc(boardId)) {
            CardSnapshot snapshot = CardSnapshot.of(card);
            if (within(card.getCreatedAt(), start, end)) {
                created.add(snapshot);
            }
            if (card.getLane().isDone()) {
                Instant completedAt = card.getCompletedAt() == null ? card.getUpdatedAt() : card.getCompletedAt();
                if (within(completedAt, start, end)) {
                    completed.add(snapshot);
                }
                continue;
            }
            if (snapshot.isBlocked()) {
                blocked.add(snapshot);
            }
            LocalDate due = card.getDueDate();
            if (due != null && due.isBefore(weekEnding)) {
                overdue.add(snapshot);
            } else if (due != null && !due.isAfter(weekEnding.plusDays(7))) {
                dueNextWeek.add(snapshot);
            }
        }
        Comparator<CardSnapshot> byDue = Comparator.comparing(CardSnapshot::dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CardSnapshot::title, String.CASE_INSENSITIVE_ORDER);
        overdue.sort(byDue);
        dueNextWeek.sort(byDue);

        int changes = 0;
        Map<String, Integer> perPerson = new HashMap<>();
        Map<String, Instant> lastActive = new HashMap<>();
        for (Activity activity : activities.findAllByBoardIdAndOccurredAtAfterOrderByOccurredAtAsc(boardId, start)) {
            if (!activity.getOccurredAt().isBefore(end)) {
                break;
            }
            changes++;
            if (!ActivityLog.SYSTEM_ACTOR.equals(activity.getActor())) {
                perPerson.merge(activity.getActor(), 1, Integer::sum);
                lastActive.put(activity.getActor(), activity.getOccurredAt());
            }
        }
        List<ActivityPulse.Contributor> contributors = perPerson.entrySet().stream()
                .map(entry -> new ActivityPulse.Contributor(entry.getKey(), entry.getValue(), lastActive.get(entry.getKey())))
                .sorted(Comparator.comparingInt(ActivityPulse.Contributor::changes).reversed()
                        .thenComparing(ActivityPulse.Contributor::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new WeeklyDigest(board.getName(), from, weekEnding, changes, completed, created, overdue, blocked, dueNextWeek, contributors);
    }

    public WeeklyDigest thisWeek(long boardId) {
        return weekly(boardId, LocalDate.now(clock));
    }

    private static boolean within(Instant moment, Instant start, Instant end) {
        return moment != null && !moment.isBefore(start) && moment.isBefore(end);
    }
}
