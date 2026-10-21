package dev.gatzka.flowboard.domain;

/** Everything a user provides to create or edit a board. */
public record BoardDraft(String name, String description) {

    public BoardDraft {
        name = Text.require(name, "Board name");
        description = Text.clean(description);
    }
}
