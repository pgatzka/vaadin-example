package dev.gatzka.flowboard.domain;

/**
 * Everything a user provides to create or edit a lane.
 *
 * @param wipLimit optional work-in-progress limit; {@code null} means unlimited
 * @param done whether cards in this lane count as completed (they are never reported as overdue)
 */
public record LaneDraft(String name, Integer wipLimit, boolean done) {

    public LaneDraft {
        name = Text.require(name, "Lane name");
        if (wipLimit != null && wipLimit < 1) {
            throw new IllegalArgumentException("WIP limit must be at least 1");
        }
    }
}
