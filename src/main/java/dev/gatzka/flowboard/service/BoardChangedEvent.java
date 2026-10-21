package dev.gatzka.flowboard.service;

/**
 * Published after a board changed. Carries a human readable description of the change so that
 * other users can be told what happened, e.g. {@code Mara} + {@code moved "Dark mode" to Done}.
 */
public record BoardChangedEvent(long boardId, Kind kind, String actor, String message) {

    public enum Kind {
        /** A new board exists. */
        CREATED,
        /** The board's own name or description changed. */
        UPDATED,
        /** The board is gone. */
        DELETED,
        /** Lanes or cards inside the board changed. */
        CONTENT
    }

    public static BoardChangedEvent created(long boardId, String actor, String message) {
        return new BoardChangedEvent(boardId, Kind.CREATED, actor, message);
    }

    public static BoardChangedEvent updated(long boardId, String actor, String message) {
        return new BoardChangedEvent(boardId, Kind.UPDATED, actor, message);
    }

    public static BoardChangedEvent deleted(long boardId, String actor, String message) {
        return new BoardChangedEvent(boardId, Kind.DELETED, actor, message);
    }

    public static BoardChangedEvent content(long boardId, String actor, String message) {
        return new BoardChangedEvent(boardId, Kind.CONTENT, actor, message);
    }

    /** Whether the list of boards (names, existence) is affected, as opposed to a board's content. */
    public boolean affectsBoardList() {
        return kind != Kind.CONTENT;
    }
}
