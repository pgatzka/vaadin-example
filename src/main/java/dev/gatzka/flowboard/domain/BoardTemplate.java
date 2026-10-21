package dev.gatzka.flowboard.domain;

import java.util.List;

/** Lane layouts offered when creating a board. */
public enum BoardTemplate {

    KANBAN("Kanban", "Backlog, In progress, Review and Done with sensible WIP limits.", List.of(
            new LaneDraft("Backlog", null, false),
            new LaneDraft("In progress", 3, false),
            new LaneDraft("Review", 2, false),
            new LaneDraft("Done", null, true))),

    SCRUM("Scrum sprint", "Product and sprint backlogs ahead of the usual flow.", List.of(
            new LaneDraft("Product backlog", null, false),
            new LaneDraft("Sprint backlog", null, false),
            new LaneDraft("In progress", 3, false),
            new LaneDraft("Review", 2, false),
            new LaneDraft("Done", null, true))),

    PERSONAL("Personal", "To do, Doing and Done. One thing at a time.", List.of(
            new LaneDraft("To do", null, false),
            new LaneDraft("Doing", 1, false),
            new LaneDraft("Done", null, true))),

    BUG_TRIAGE("Bug triage", "New, Confirmed, Fixing, Verifying and Closed.", List.of(
            new LaneDraft("New", null, false),
            new LaneDraft("Confirmed", null, false),
            new LaneDraft("Fixing", 3, false),
            new LaneDraft("Verifying", null, false),
            new LaneDraft("Closed", null, true))),

    BLANK("Blank", "No lanes. Build your own structure.", List.of());

    private final String label;
    private final String description;
    private final List<LaneDraft> lanes;

    BoardTemplate(String label, String description, List<LaneDraft> lanes) {
        this.label = label;
        this.description = description;
        this.lanes = lanes;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public List<LaneDraft> lanes() {
        return lanes;
    }
}
