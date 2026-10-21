package dev.gatzka.flowboard.domain;

import java.time.LocalDate;
import java.time.Period;

/** How often a card comes back after it is finished. */
public enum Recurrence {
    NONE("Never", null),
    DAILY("Every day", Period.ofDays(1)),
    WEEKLY("Every week", Period.ofWeeks(1)),
    EVERY_TWO_WEEKS("Every two weeks", Period.ofWeeks(2)),
    MONTHLY("Every month", Period.ofMonths(1));

    private final String label;
    private final Period period;

    Recurrence(String label, Period period) {
        this.label = label;
        this.period = period;
    }

    public String label() {
        return label;
    }

    public boolean repeats() {
        return period != null;
    }

    /**
     * The due date of the next occurrence. Counting starts from the previous due date so a weekly
     * card stays on its weekday even when it was finished late; a date in the past is skipped
     * forward until it is after today. Without a due date there is nothing to compute.
     */
    public LocalDate next(LocalDate previousDue, LocalDate today) {
        if (!repeats() || previousDue == null) {
            return null;
        }
        int steps = 1;
        LocalDate next = previousDue.plus(period);
        while (!next.isAfter(today)) {
            next = previousDue.plus(period.multipliedBy(++steps)); // from the origin, so month ends do not drift
        }
        return next;
    }
}
