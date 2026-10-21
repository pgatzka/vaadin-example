package dev.gatzka.flowboard.assistant;

/** The assistant could not produce an answer. The message is safe to show to the user. */
public class AssistantException extends RuntimeException {

    public AssistantException(String message) {
        super(message);
    }

    public AssistantException(String message, Throwable cause) {
        super(message, cause);
    }
}
