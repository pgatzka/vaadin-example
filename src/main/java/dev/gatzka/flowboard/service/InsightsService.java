package dev.gatzka.flowboard.service;

import dev.gatzka.flowboard.domain.Activity;
import dev.gatzka.flowboard.domain.ActivityPulse;
import dev.gatzka.flowboard.domain.BoardInsights;
import dev.gatzka.flowboard.domain.BoardInsights.AssigneeLoad;
import dev.gatzka.flowboard.domain.BoardInsights.DailyCount;
import dev.gatzka.flowboard.domain.BoardInsights.LaneLoad;
import dev.gatzka.flowboard.domain.Card;
import dev.gatzka.flowboard.domain.Forecast;
import dev.gatzka.flowboard.domain.Lane;
import dev.gatzka.flowboard.domain.Priority;
import dev.gatzka.flowboard.repository.ActivityRepository;
import dev.gatzka.flowboard.repository.CardRepository;
import dev.gatzka.flowboard.repository.LaneRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Computes {@link BoardInsights}. Read-only. */
@Service
@Transactional(readOnly = true)
public class InsightsService {

    static final int HISTORY_DAYS = 14;
    static final int VELOCITY_WEEKS = 4;

    private final LaneRepository lanes;
    private final CardRepository cards;
    private final ActivityRepository activities;
    private final Clock clock;

    InsightsService(LaneRepository lanes, CardRepository cards, Clock clock, ActivityRepository activities) {
        this.activities = activities;
        this.lanes = lanes;
        this.cards = cards;
        this.clock = clock;
    }

    /** Activity per day over the last {@link ActivityPulse#WEEKS} whole weeks, and who caused it. */
    public ActivityPulse pulse(long boardId) {
        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.now(clock);
        LocalDate start = today.minusDays(7L * ActivityPulse.WEEKS - 1);
        Map<LocalDate, Integer> perDay = new TreeMap<>();
        Map<String, Integer> perPerson = new HashMap<>();
        Map<String, Instant> lastActive = new HashMap<>();
        for (Activity activity : activities.findAllByBoardIdAndOccurredAtAfterOrderByOccurredAtAsc(boardId, start.atStartOfDay(zone).toInstant())) {
            perDay.merge(LocalDate.ofInstant(activity.getOccurredAt(), zone), 1, Integer::sum);
            if (!ActivityLog.SYSTEM_ACTOR.equals(activity.getActor())) {
                perPerson.merge(activity.getActor(), 1, Integer::sum);
                lastActive.put(activity.getActor(), activity.getOccurredAt());
            }
        }
        List<ActivityPulse.DayCount> days = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
            days.add(new ActivityPulse.DayCount(day, perDay.getOrDefault(day, 0)));
        }
        List<ActivityPulse.Contributor> contributors = perPerson.entrySet().stream()
                .map(entry -> new ActivityPulse.Contributor(entry.getKey(), entry.getValue(), lastActive.get(entry.getKey())))
                .sorted(Comparator.comparingInt(ActivityPulse.Contributor::changes).reversed()
                        .thenComparing(ActivityPulse.Contributor::name, String.CASE_INSENSITIVE_ORDER))
                .limit(8)
                .toList();
        return new ActivityPulse(days, contributors);
    }

    /** When the open cards will be done, simulated from the board's completions of the last {@value Forecast#HISTORY_DAYS} days. */
    public Forecast forecast(long boardId) {
        List<Card> open = cards.findAllByLaneBoardIdAndArchivedAtIsNullOrderByPositionAsc(boardId).stream()
                .filter(card -> !card.getLane().isDone())
                .toList();
        LocalDate target = open.stream().map(Card::getDueDate).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        return Forecast.of(open.size(), throughput(boardId), LocalDate.now(clock), target, boardId);
    }

    /** Cards completed on each of the last {@value Forecast#HISTORY_DAYS} days, oldest first, archived cards included. */
    public List<Integer> throughput(long boardId) {
        ZoneId zone = clock.getZone();
        LocalDate firstDay = LocalDate.now(clock).minusDays(Forecast.HISTORY_DAYS - 1L);
        int[] perDay = new int[Forecast.HISTORY_DAYS];
        for (Card card : cards.findAllByLaneBoardIdAndCompletedAtGreaterThanEqual(boardId, firstDay.atStartOfDay(zone).toInstant())) {
            int index = (int) ChronoUnit.DAYS.between(firstDay, LocalDate.ofInstant(card.getCompletedAt(), zone));
            if (index >= 0 && index < perDay.length) {
                perDay[index]++;
            }
        }
        return Arrays.stream(perDay).boxed().toList();
    }

    public BoardInsights forBoard(long boardId) {
        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.now(clock);
        Instant weekAgo = today.minusDays(7).atStartOfDay(zone).toInstant();
        Instant velocityWindow = today.minusDays(7L * VELOCITY_WEEKS).atStartOfDay(zone).toInstant();
        LocalDate weekAhead = today.plusDays(7);

        List<Lane> boardLanes = lanes.findAllByBoardIdOrderByPositionAsc(boardId);
        List<Card> boardCards = cards.findAllByLaneBoardIdAndArchivedAtIsNullOrderByPositionAsc(boardId);

        Map<Long, Integer> cardsPerLane = new TreeMap<>();
        Map<Long, Duration> timeInLanePerLane = new TreeMap<>();
        Instant now = clock.instant();
        Map<Priority, Integer> openByPriority = new EnumMap<>(Priority.class);
        Map<String, Integer> openByAssignee = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<LocalDate, Integer> completedPerDay = new TreeMap<>();
        int completed = 0;
        int overdue = 0;
        int dueThisWeek = 0;
        int completedLast7Days = 0;
        int openPoints = 0;
        int pointsInVelocityWindow = 0;
        Duration cycleTimeSum = Duration.ZERO;

        for (Card card : boardCards) {
            cardsPerLane.merge(card.getLane().getId(), 1, Integer::sum);
            timeInLanePerLane.merge(card.getLane().getId(), Duration.between(card.getLaneEnteredAt(), now), Duration::plus);
            if (card.getLane().isDone()) {
                completed++;
                Instant completedAt = card.getCompletedAt() == null ? card.getUpdatedAt() : card.getCompletedAt();
                cycleTimeSum = cycleTimeSum.plus(Duration.between(card.getCreatedAt(), completedAt));
                if (!completedAt.isBefore(weekAgo)) {
                    completedLast7Days++;
                }
                if (!completedAt.isBefore(velocityWindow) && card.getEstimate() != null) {
                    pointsInVelocityWindow += card.getEstimate();
                }
                completedPerDay.merge(LocalDate.ofInstant(completedAt, zone), 1, Integer::sum);
                continue;
            }
            openByPriority.merge(card.getPriority(), 1, Integer::sum);
            if (card.getEstimate() != null) {
                openPoints += card.getEstimate();
            }
            if (card.getAssignee() != null) {
                openByAssignee.merge(card.getAssignee(), 1, Integer::sum);
            }
            LocalDate due = card.getDueDate();
            if (due != null && due.isBefore(today)) {
                overdue++;
            } else if (due != null && !due.isAfter(weekAhead)) {
                dueThisWeek++;
            }
        }

        List<LaneLoad> laneLoads = boardLanes.stream()
                .map(lane -> {
                    int count = cardsPerLane.getOrDefault(lane.getId(), 0);
                    Optional<Duration> average = count == 0 ? Optional.empty()
                            : Optional.of(timeInLanePerLane.get(lane.getId()).dividedBy(count));
                    return new LaneLoad(lane.getName(), count, lane.getWipLimit(), lane.isDone(), average);
                })
                .toList();
        List<AssigneeLoad> assigneeLoads = openByAssignee.entrySet().stream()
                .map(entry -> new AssigneeLoad(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(AssigneeLoad::openCards).reversed().thenComparing(AssigneeLoad::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<DailyCount> dailyCounts = new ArrayList<>(HISTORY_DAYS);
        for (int i = HISTORY_DAYS - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            dailyCounts.add(new DailyCount(day, completedPerDay.getOrDefault(day, 0)));
        }
        Optional<Duration> averageCycleTime = completed == 0 ? Optional.empty() : Optional.of(cycleTimeSum.dividedBy(completed));

        return new BoardInsights(boardCards.size(), completed, overdue, dueThisWeek, completedLast7Days, averageCycleTime,
                openPoints, (double) pointsInVelocityWindow / VELOCITY_WEEKS, laneLoads, openByPriority, assigneeLoads, dailyCounts);
    }
}
