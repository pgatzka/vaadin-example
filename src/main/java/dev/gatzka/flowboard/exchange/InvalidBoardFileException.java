package dev.gatzka.flowboard.exchange;

/** The uploaded file cannot be imported as a board. The message is safe to show to the user. */
public class InvalidBoardFileException extends RuntimeException {

    public InvalidBoardFileException(String message) {
        super(message);
    }

    public InvalidBoardFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
