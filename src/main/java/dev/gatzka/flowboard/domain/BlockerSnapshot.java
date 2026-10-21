package dev.gatzka.flowboard.domain;

/** A card that blocks another, with whether it still does. */
public record BlockerSnapshot(long id, String title, boolean resolved) {

    static BlockerSnapshot of(Card blocker) {
        return new BlockerSnapshot(blocker.getId(), blocker.getTitle(), blocker.isResolvedAsBlocker());
    }
}
