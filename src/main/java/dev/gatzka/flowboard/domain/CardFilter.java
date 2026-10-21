package dev.gatzka.flowboard.domain;

import java.time.LocalDate;

/**
 * What the board is currently narrowed down to. Immutable; use the {@code with…} methods.
 *
 * @param text free text matched against title, description, assignee and tags
 * @param priority only cards with exactly this priority, or {@code null} for any
 * @param overdueOnly only cards whose due date has passed (and that are not in a done lane)
 * @param mineOnly only cards assigned to the current user
 * @param unassignedOnly only cards without an assignee
 * @param tag only cards carrying this tag, or {@code null} for any
 * @param assignee only cards assigned to this person, or {@code null} for anybody
 */
public record CardFilter(String text, Priority priority, boolean overdueOnly, boolean mineOnly, boolean unassignedOnly,
        String tag, String assignee) {

    private static final CardFilter NONE = new CardFilter("", null, false, false, false, null, null);

    public CardFilter {
        text = text == null ? "" : text.strip();
        tag = Text.clean(tag);
        assignee = Text.clean(assignee);
    }

    public static CardFilter none() {
        return NONE;
    }

    public CardFilter withText(String text) {
        return new CardFilter(text, priority, overdueOnly, mineOnly, unassignedOnly, tag, assignee);
    }

    public CardFilter withPriority(Priority priority) {
        return new CardFilter(text, priority, overdueOnly, mineOnly, unassignedOnly, tag, assignee);
    }

    public CardFilter withOverdueOnly(boolean overdueOnly) {
        return new CardFilter(text, priority, overdueOnly, mineOnly, unassignedOnly, tag, assignee);
    }

    public CardFilter withMineOnly(boolean mineOnly) {
        return new CardFilter(text, priority, overdueOnly, mineOnly, unassignedOnly, tag, assignee);
    }

    public CardFilter withUnassignedOnly(boolean unassignedOnly) {
        return new CardFilter(text, priority, overdueOnly, mineOnly, unassignedOnly, tag, assignee);
    }

    public CardFilter withTag(String tag) {
        return new CardFilter(text, priority, overdueOnly, mineOnly, unassignedOnly, tag, assignee);
    }

    public CardFilter withAssignee(String assignee) {
        return new CardFilter(text, priority, overdueOnly, mineOnly, unassignedOnly, tag, assignee);
    }

    /** Clears everything except the free text, which lives in its own field in the UI. */
    public CardFilter withoutToggles() {
        return new CardFilter(text, null, false, false, false, null, null);
    }

    /** Number of active criteria other than the free text. */
    public int toggleCount() {
        int count = priority == null ? 0 : 1;
        if (overdueOnly) {
            count++;
        }
        if (mineOnly) {
            count++;
        }
        if (unassignedOnly) {
            count++;
        }
        if (tag != null) {
            count++;
        }
        if (assignee != null) {
            count++;
        }
        return count;
    }

    public boolean isActive() {
        return !text.isEmpty() || toggleCount() > 0;
    }

    /**
     * @param lane the lane the card is in, needed because done lanes have no overdue cards
     * @param me the current user's display name, compared case-insensitively with the assignee
     */
    public boolean matches(CardSnapshot card, LaneSnapshot lane, LocalDate today, String me) {
        if (!card.matches(text)) {
            return false;
        }
        if (priority != null && card.priority() != priority) {
            return false;
        }
        if (overdueOnly && (lane.done() || !card.isOverdue(today))) {
            return false;
        }
        if (mineOnly && (card.assignee() == null || !card.assignee().equalsIgnoreCase(me))) {
            return false;
        }
        if (unassignedOnly && card.assignee() != null) {
            return false;
        }
        if (tag != null && card.tags().stream().noneMatch(tag::equalsIgnoreCase)) {
            return false;
        }
        return assignee == null || (card.assignee() != null && card.assignee().equalsIgnoreCase(assignee));
    }
}
