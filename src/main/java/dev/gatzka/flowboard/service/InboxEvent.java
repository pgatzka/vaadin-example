package dev.gatzka.flowboard.service;

/** Published after a notification for {@code recipientKey} was stored. */
public record InboxEvent(String recipientKey) {
}
