package dev.gatzka.flowboard.domain;

import java.util.List;

/** Who may open a board: everybody, or only the listed members. */
public record BoardAccess(long boardId, boolean membersOnly, List<String> members) {

    public BoardAccess {
        members = List.copyOf(members);
    }
}
