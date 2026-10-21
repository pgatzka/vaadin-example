package dev.gatzka.flowboard.domain;

import java.time.Duration;
import java.time.Instant;

/** How long a card has been sitting in its lane, bucketed for display. */
public record CardAge(Duration inLane, Level level) {

    /** Cards older than this in a lane are called out; older than {@link #STALE_AFTER} they fade. */
    public static final Duration AGING_AFTER = Duration.ofDays(2);
    public static final Duration STALE_AFTER = Duration.ofDays(7);

    public enum Level {
        FRESH,
        AGING,
        STALE
    }

    public static CardAge of(Instant laneEnteredAt, Instant now) {
        Duration inLane = Duration.between(laneEnteredAt, now);
        if (inLane.isNegative()) {
            inLane = Duration.ZERO;
        }
        Level level = inLane.compareTo(STALE_AFTER) >= 0 ? Level.STALE
                : inLane.compareTo(AGING_AFTER) >= 0 ? Level.AGING
                : Level.FRESH;
        return new CardAge(inLane, level);
    }

    public long days() {
        return inLane.toDays();
    }

    public boolean isNoteworthy() {
        return level != Level.FRESH;
    }
}
