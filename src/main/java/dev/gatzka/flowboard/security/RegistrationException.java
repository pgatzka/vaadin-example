package dev.gatzka.flowboard.security;

/** A registration request that cannot be honoured; the message is safe to show to the user. */
public class RegistrationException extends RuntimeException {

    public RegistrationException(String message) {
        super(message);
    }
}
