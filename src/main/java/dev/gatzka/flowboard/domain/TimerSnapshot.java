package dev.gatzka.flowboard.domain;

import java.time.Duration;
import java.time.Instant;

/** A running timer as the UI shows it; {@code endsAt} is set for a focus session. */
public record TimerSnapshot(long cardId, long boardId, String cardTitle, Instant startedAt, Instant endsAt) {

    public static TimerSnapshot of(RunningTimer timer) {
        Card card = timer.getCard();
        return new TimerSnapshot(card.getId(), card.getLane().getBoard().getId(), card.getTitle(), timer.getStartedAt(), timer.getEndsAt());
    }

    public boolean isFocusSession() {
        return endsAt != null;
    }

    public Duration remaining(Instant now) {
        return endsAt == null ? Duration.ZERO : Duration.between(now, endsAt);
    }

    public Duration elapsed(Instant now) {
        return Duration.between(startedAt, now);
    }
}
