package dev.gatzka.flowboard.service;

/** Thrown when an operation targets a board, lane or card that no longer exists. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String what, long id) {
        super("%s %d not found".formatted(what, id));
    }
}
