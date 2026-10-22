package dev.gatzka.flowboard.domain;

import java.util.Locale;
import java.util.Set;

/** Something a card must have before it belongs in a lane: the argument of a {@link RuleAction#REQUIRE} rule. */
public enum Requirement {
    ASSIGNEE("assignee", "owner", "assigned"),
    ESTIMATE("estimate", "points", "estimated"),
    DUE_DATE("due", "due date", "duedate", "deadline"),
    DESCRIPTION("description", "text"),
    CHECKLIST("checklist", "checklist done", "checklist complete");

    private final Set<String> spellings;

    Requirement(String... spellings) {
        this.spellings = Set.of(spellings);
    }

    /** Accepts the enum name or any everyday spelling, case-insensitively; throws when nothing matches. */
    public static Requirement parse(String text) {
        String cleaned = text == null ? "" : text.strip().toLowerCase(Locale.ROOT).replace('_', ' ');
        for (Requirement requirement : values()) {
            if (requirement.name().toLowerCase(Locale.ROOT).replace('_', ' ').equals(cleaned) || requirement.spellings.contains(cleaned)) {
                return requirement;
            }
        }
        throw new IllegalArgumentException("Require one of: assignee, estimate, due date, description, checklist");
    }

    public boolean isMetBy(CardSnapshot card) {
        return switch (this) {
            case ASSIGNEE -> card.assignee() != null && !card.assignee().isBlank();
            case ESTIMATE -> card.estimate() != null;
            case DUE_DATE -> card.dueDate() != null;
            case DESCRIPTION -> card.description() != null && !card.description().isBlank();
            case CHECKLIST -> card.hasChecklist() && card.isChecklistComplete();
        };
    }

    /** The English noun phrase for messages outside the UI, e.g. "an estimate". */
    public String phrase() {
        return switch (this) {
            case ASSIGNEE -> "an assignee";
            case ESTIMATE -> "an estimate";
            case DUE_DATE -> "a due date";
            case DESCRIPTION -> "a description";
            case CHECKLIST -> "a completed checklist";
        };
    }
}
