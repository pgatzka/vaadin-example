package dev.gatzka.flowboard.domain;

import java.time.Instant;

public record WorkEntrySnapshot(long id, String person, int minutes, String note, Instant loggedAt) {

    public static WorkEntrySnapshot of(WorkEntry entry) {
        return new WorkEntrySnapshot(entry.getId(), entry.getPerson(), entry.getMinutes(), entry.getNote(), entry.getLoggedAt());
    }
}
