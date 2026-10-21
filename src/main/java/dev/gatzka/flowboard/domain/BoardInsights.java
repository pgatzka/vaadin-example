package dev.gatzka.flowboard.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Numbers about one board, computed on demand from its cards.
 *
 * @param completedLast7Days cards that entered a done lane during the last seven days
 * @param averageCycleTime mean time from creation to completion over all completed cards
 * @param completedPerDay one entry per day for the last {@code n} days, oldest first
 * @param openPoints story points of all open cards
 * @param velocityPerWeek story points completed per week, averaged over the last four weeks
 */
public record BoardInsights(
        int totalCards,
        int completedCards,
        int overdueCards,
        int dueThisWeek,
        int completedLast7Days,
        Optional<Duration> averageCycleTime,
        int openPoints,
        double velocityPerWeek,
        List<LaneLoad> lanes,
        Map<Priority, Integer> openByPriority,
        List<AssigneeLoad> openByAssignee,
        List<DailyCount> completedPerDay) {

    /** @param averageTimeInLane mean time the lane's current cards have spent there; empty when the lane is empty */
    public record LaneLoad(String name, int cards, Integer wipLimit, boolean done, Optional<Duration> averageTimeInLane) {

        public boolean isOverWipLimit() {
            return wipLimit != null && cards > wipLimit;
        }
    }

    public record AssigneeLoad(String name, int openCards) {
    }

    public record DailyCount(LocalDate day, int count) {
    }

    public int openCards() {
        return totalCards - completedCards;
    }

    /** Share of cards that are completed, 0 to 1. */
    public double completionRatio() {
        return totalCards == 0 ? 0 : (double) completedCards / totalCards;
    }

    public int busiestLaneCount() {
        return lanes.stream().mapToInt(LaneLoad::cards).max().orElse(0);
    }

    public int busiestDayCount() {
        return completedPerDay.stream().mapToInt(DailyCount::count).max().orElse(0);
    }
}
