package dev.gatzka.flowboard.ui.support;

import java.time.Duration;

/** Human friendly rendering of durations, e.g. "3 h" or "2.5 d". */
public final class Durations {

    private Durations() {
    }

    public static String humanize(Duration duration) {
        long minutes = duration.toMinutes();
        if (minutes < 60) {
            return minutes + " min";
        }
        long hours = duration.toHours();
        if (hours < 48) {
            return hours + " h";
        }
        double days = duration.toMinutes() / (60.0 * 24);
        return (days >= 10 ? "%.0f d" : "%.1f d").formatted(days);
    }
}
