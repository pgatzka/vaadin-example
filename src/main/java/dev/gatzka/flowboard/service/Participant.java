package dev.gatzka.flowboard.service;

import java.io.Serializable;

/**
 * Somebody looking at a board.
 *
 * @param colorIndex index into the seven Vaadin user colours, derived from the name so the same
 *        person gets the same colour everywhere
 */
public record Participant(String name, int colorIndex) implements Serializable {

    public static final int COLOR_COUNT = 7;

    public static Participant named(String name) {
        return new Participant(name, Math.floorMod(name.hashCode(), COLOR_COUNT));
    }
}
