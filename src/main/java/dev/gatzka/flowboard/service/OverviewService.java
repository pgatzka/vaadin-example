package dev.gatzka.flowboard.service;

import dev.gatzka.flowboard.domain.Activity;
import dev.gatzka.flowboard.domain.ActivityEntry;
import dev.gatzka.flowboard.domain.Board;
import dev.gatzka.flowboard.domain.Card;
import dev.gatzka.flowboard.domain.CardLocation;
import dev.gatzka.flowboard.domain.Overview;
import dev.gatzka.flowboard.domain.Overview.ActivityLocation;
import dev.gatzka.flowboard.repository.ActivityRepository;
import dev.gatzka.flowboard.repository.BoardRepository;
import dev.gatzka.flowboard.repository.CardRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Numbers and lists for the home page, computed across all boards. */
@Service
@Transactional(readOnly = true)
public class OverviewService {

    static final int ATTENTION_LIMIT = 8;
    static final int ACTIVITY_LIMIT = 12;

    private final BoardRepository boards;
    private final CardRepository cards;
    private final ActivityRepository activities;
    private final Clock clock;

    OverviewService(BoardRepository boards, CardRepository cards, ActivityRepository activities, Clock clock) {
        this.boards = boards;
        this.cards = cards;
        this.activities = activities;
        this.clock = clock;
    }

    public Overview overview() {
        return overview(null);
    }

    /** The overview restricted to the given boards; {@code null} means every board. */
    public Overview overview(java.util.Set<Long> boardIds) {
        LocalDate today = LocalDate.now(clock);
        LocalDate weekAhead = today.plusDays(7);
        Instant weekAgo = today.minusDays(7).atStartOfDay(clock.getZone()).toInstant();
        Map<Long, String> boardNames = boards.findAll().stream()
                .filter(board -> boardIds == null || boardIds.contains(board.getId()))
                .collect(Collectors.toMap(Board::getId, Board::getName));

        int open = 0;
        int overdue = 0;
        int dueThisWeek = 0;
        int completedLast7Days = 0;
        List<CardLocation> attention = new java.util.ArrayList<>();
        for (Card card : cards.findAllByArchivedAtIsNull()) {
            if (!boardNames.containsKey(card.getLane().getBoard().getId())) {
                continue;
            }
            if (card.getLane().isDone()) {
                Instant completedAt = card.getCompletedAt() == null ? card.getUpdatedAt() : card.getCompletedAt();
                if (!completedAt.isBefore(weekAgo)) {
                    completedLast7Days++;
                }
                continue;
            }
            open++;
            LocalDate due = card.getDueDate();
            if (due != null && due.isBefore(today)) {
                overdue++;
                attention.add(CardLocation.of(card));
            } else if (due != null && !due.isAfter(weekAhead)) {
                dueThisWeek++;
            }
        }
        attention.sort(Comparator.comparing((CardLocation location) -> location.card().dueDate())
                .thenComparing(location -> location.card().title(), String.CASE_INSENSITIVE_ORDER));

        List<ActivityLocation> recent = activities.findTop50ByOrderByOccurredAtDesc().stream()
                .filter(activity -> boardNames.containsKey(activity.getBoardId()))
                .limit(ACTIVITY_LIMIT)
                .map(activity -> new ActivityLocation(activity.getBoardId(), boardNames.get(activity.getBoardId()), ActivityEntry.of(activity)))
                .toList();

        return new Overview(boardNames.size(), open, overdue, dueThisWeek, completedLast7Days,
                attention.stream().limit(ATTENTION_LIMIT).toList(), recent);
    }
}
