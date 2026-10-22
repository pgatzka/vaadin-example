package dev.gatzka.flowboard.ui.support;

import dev.gatzka.flowboard.ui.i18n.I18n;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/** Human friendly rendering of durations in the current language, e.g. "3 h" or "2.5 d". */
public final class Durations {

    private Durations() {
    }

    /** How long ago a moment was, e.g. "just now", "5 min ago" or "3 h ago". */
    public static String ago(Instant moment, Instant now) {
        Duration elapsed = Duration.between(moment, now);
        if (elapsed.toMinutes() < 1) {
            return I18n.t("duration.justNow");
        }
        return I18n.t("duration.ago", humanize(elapsed));
    }

    /** The elapsed time without "ago", e.g. "5 min", for "running since 5 min" style sentences. */
    public static String since(Instant moment, Instant now) {
        return humanize(Duration.between(moment, now));
    }

    public static String humanize(Duration duration) {
        long minutes = duration.toMinutes();
        if (minutes < 60) {
            return I18n.t("duration.minutes", Long.toString(minutes));
        }
        long hours = duration.toHours();
        if (hours < 48) {
            return I18n.t("duration.hours", Long.toString(hours));
        }
        double days = duration.toMinutes() / (60.0 * 24);
        return I18n.t("duration.days", String.format(I18n.locale(), days >= 10 ? "%.0f" : "%.1f", days));
    }

    /** Package-private hook for tests that run without a UI. */
    static Locale locale() {
        return I18n.locale();
    }
}
