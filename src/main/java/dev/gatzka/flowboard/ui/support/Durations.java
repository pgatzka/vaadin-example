package dev.gatzka.flowboard.ui.support;

import java.time.Duration;
import java.time.Instant;

/** Human friendly rendering of durations, e.g. "3 h" or "2.5 d". */
public final class Durations {

    private Durations() {
    }

    /** How long ago a moment was, e.g. "just now", "5 min ago" or "3 h ago". */
    public static String ago(Instant moment, Instant now) {
        Duration elapsed = Duration.between(moment, now);
        if (elapsed.toMinutes() < 1) {
            return "just now";
        }
        return humanize(elapsed) + " ago";
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
