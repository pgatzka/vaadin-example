package dev.gatzka.flowboard.domain;

import java.time.LocalDate;

/** What it takes to create or change a milestone. */
public record MilestoneDraft(String name, LocalDate targetDate) {

    public static final int MAX_NAME = 120;

    public MilestoneDraft {
        name = Text.clean(name);
        if (name == null) {
            throw new IllegalArgumentException("A milestone needs a name");
        }
        if (name.length() > MAX_NAME) {
            throw new IllegalArgumentException("A milestone name has at most " + MAX_NAME + " characters");
        }
    }
}
