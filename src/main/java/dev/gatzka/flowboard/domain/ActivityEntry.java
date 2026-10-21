package dev.gatzka.flowboard.domain;

import java.time.Instant;

public record ActivityEntry(String actor, String message, Instant occurredAt) {

    public static ActivityEntry of(Activity activity) {
        return new ActivityEntry(activity.getActor(), activity.getMessage(), activity.getOccurredAt());
    }
}
