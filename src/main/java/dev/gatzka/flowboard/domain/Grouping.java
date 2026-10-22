package dev.gatzka.flowboard.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** How a board is split into swimlanes: one row per assignee, priority or tag, across all lanes. */
public enum Grouping {
    NONE("None"),
    ASSIGNEE("Assignee"),
    PRIORITY("Priority"),
    TAG("Tag");

    public static final String UNASSIGNED = "Unassigned";
    public static final String NO_TAG = "No tag";

    private final String label;

    Grouping(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isActive() {
        return this != NONE;
    }

    /** The row a card belongs to; a card with several tags goes with its first one alphabetically. */
    public String keyOf(CardSnapshot card) {
        return switch (this) {
            case NONE -> "";
            case ASSIGNEE -> card.assignee() == null || card.assignee().isBlank() ? UNASSIGNED : card.assignee().strip();
            case PRIORITY -> card.priority().label();
            case TAG -> card.tags().stream().min(String.CASE_INSENSITIVE_ORDER).orElse(NO_TAG);
        };
    }

    /** Every row the board needs, in display order: priorities from urgent down, names alphabetically, catch-alls last. */
    public List<String> rows(BoardSnapshot board) {
        return switch (this) {
            case NONE -> List.of();
            case PRIORITY -> {
                List<Priority> priorities = new ArrayList<>(List.of(Priority.values()));
                priorities.sort(Comparator.reverseOrder());
                yield priorities.stream().map(Priority::label).toList();
            }
            case ASSIGNEE, TAG -> {
                Set<String> keys = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                Set<String> seen = new HashSet<>();
                boolean catchAll = false;
                for (LaneSnapshot lane : board.lanes()) {
                    for (CardSnapshot card : lane.cards()) {
                        String key = keyOf(card);
                        if (isCatchAll(key)) {
                            catchAll = true;
                        } else if (seen.add(key.toLowerCase(Locale.ROOT))) {
                            keys.add(key);
                        }
                    }
                }
                List<String> rows = new ArrayList<>(keys);
                if (catchAll || rows.isEmpty()) {
                    rows.add(this == ASSIGNEE ? UNASSIGNED : NO_TAG);
                }
                yield rows;
            }
        };
    }

    private boolean isCatchAll(String key) {
        return this == ASSIGNEE ? UNASSIGNED.equals(key) : NO_TAG.equals(key);
    }

    /** The draft with the card moved into the given row: assigned, re-prioritised or tagged accordingly. */
    public CardDraft moveInto(CardDraft draft, String row) {
        return switch (this) {
            case NONE -> draft;
            case ASSIGNEE -> new CardDraft(draft.title(), draft.description(), draft.priority(), draft.dueDate(),
                    UNASSIGNED.equals(row) ? null : row, draft.tags(), draft.estimate());
            case PRIORITY -> new CardDraft(draft.title(), draft.description(), priorityOf(row), draft.dueDate(),
                    draft.assignee(), draft.tags(), draft.estimate());
            case TAG -> {
                Set<String> tags = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                if (draft.tags() != null) {
                    tags.addAll(draft.tags());
                }
                if (NO_TAG.equals(row)) {
                    tags.clear();
                } else {
                    tags.add(row);
                }
                yield new CardDraft(draft.title(), draft.description(), draft.priority(), draft.dueDate(),
                        draft.assignee(), tags, draft.estimate());
            }
        };
    }

    private static Priority priorityOf(String label) {
        for (Priority priority : Priority.values()) {
            if (priority.label().equalsIgnoreCase(label)) {
                return priority;
            }
        }
        throw new IllegalArgumentException("Unknown priority " + label);
    }
}
