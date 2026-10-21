package dev.gatzka.flowboard.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * How busy a board has been lately: one count per day for a window of whole weeks ending today,
 * oldest first, and who did the most.
 *
 * @param contributors people by number of changes in the window, busiest first
 */
public record ActivityPulse(List<DayCount> days, List<Contributor> contributors) {

    public static final int WEEKS = 12;

    public record DayCount(LocalDate day, int count) {
    }

    public record Contributor(String name, int changes, Instant lastActive) {
    }

    public ActivityPulse {
        days = List.copyOf(days);
        contributors = List.copyOf(contributors);
    }

    public int totalChanges() {
        return days.stream().mapToInt(DayCount::count).sum();
    }

    public int busiestDayCount() {
        return days.stream().mapToInt(DayCount::count).max().orElse(0);
    }

    public long activeDays() {
        return days.stream().filter(day -> day.count() > 0).count();
    }

    /** Days in a row with activity, counting back from today (or from yesterday if today is quiet so far). */
    public int currentStreak() {
        int streak = 0;
        int index = days.size() - 1;
        if (index >= 0 && days.get(index).count() == 0) {
            index--;
        }
        while (index >= 0 && days.get(index).count() > 0) {
            streak++;
            index--;
        }
        return streak;
    }

    public boolean isQuiet() {
        return totalChanges() == 0;
    }
}
