package dev.gatzka.flowboard.domain;

import java.time.Instant;

/** A board as shown in the board list: name plus a few headline numbers. */
public record BoardSummary(
        long id,
        String name,
        String description,
        long laneCount,
        long cardCount,
        long overdueCount,
        Instant createdAt) {

    public BoardDraft toDraft() {
        return new BoardDraft(name, description);
    }
}
