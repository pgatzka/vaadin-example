package dev.gatzka.flowboard.domain;

import java.time.LocalDate;

/** A milestone as the UI and the API see it. */
public record MilestoneSnapshot(long id, long boardId, String name, LocalDate targetDate) {

    public static MilestoneSnapshot of(Milestone milestone) {
        return new MilestoneSnapshot(milestone.getId(), milestone.getBoardId(), milestone.getName(), milestone.getTargetDate());
    }

    public MilestoneDraft toDraft() {
        return new MilestoneDraft(name, targetDate);
    }

    public boolean hasTarget() {
        return targetDate != null;
    }
}
