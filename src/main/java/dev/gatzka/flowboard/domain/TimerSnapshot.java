package dev.gatzka.flowboard.domain;

import java.time.Duration;
import java.time.Instant;

/** A running timer as the UI shows it. */
public record TimerSnapshot(long cardId, long boardId, String cardTitle, Instant startedAt) {

    public static TimerSnapshot of(RunningTimer timer) {
        Card card = timer.getCard();
        return new TimerSnapshot(card.getId(), card.getLane().getBoard().getId(), card.getTitle(), timer.getStartedAt());
    }

    public Duration elapsed(Instant now) {
        return Duration.between(startedAt, now);
    }
}
