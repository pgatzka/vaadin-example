package dev.gatzka.flowboard.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * Daily card counts per lane over a period, aligned so every series has one value per day.
 *
 * @param series lanes in board order
 * @param recordedDays how many of the days actually have a recorded value (the rest are carried forward)
 */
public record CumulativeFlow(List<LocalDate> days, List<Series> series, int recordedDays) {

    public record Series(String laneName, boolean done, List<Integer> counts) {
    }

    /** A diagram needs at least two recorded days to show a trend rather than a spike. */
    public boolean hasEnoughData() {
        return recordedDays >= 2 && series.stream().anyMatch(s -> s.counts().stream().anyMatch(count -> count > 0));
    }

    /** Highest stacked total on any day, at least 1 so charts always have a scale. */
    public int maxTotal() {
        int max = 1;
        for (int i = 0; i < days.size(); i++) {
            int total = 0;
            for (Series s : series) {
                total += s.counts().get(i);
            }
            max = Math.max(max, total);
        }
        return max;
    }
}
