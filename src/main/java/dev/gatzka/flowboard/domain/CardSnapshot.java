package dev.gatzka.flowboard.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

public record CardSnapshot(
        long id,
        long laneId,
        String title,
        String description,
        Priority priority,
        LocalDate dueDate,
        String assignee,
        Set<String> tags,
        Integer estimate,
        Set<String> voters,
        List<ChecklistItemSnapshot> checklist,
        List<CommentSnapshot> comments,
        int position,
        Instant updatedAt,
        Instant archivedAt,
        Instant laneEnteredAt) {

    public CardSnapshot {
        checklist = List.copyOf(checklist);
        comments = List.copyOf(comments);
    }

    /** Cards due within this many days (inclusive) are flagged as due soon. */
    private static final int DUE_SOON_DAYS = 2;

    public static CardSnapshot of(Card card) {
        TreeSet<String> sortedTags = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        sortedTags.addAll(card.getTags());
        return new CardSnapshot(
                card.getId(),
                card.getLane().getId(),
                card.getTitle(),
                card.getDescription(),
                card.getPriority(),
                card.getDueDate(),
                card.getAssignee(),
                Collections.unmodifiableSet(sortedTags),
                card.getEstimate(),
                Set.copyOf(card.getVoters()),
                card.getChecklist().stream().map(ChecklistItemSnapshot::of).toList(),
                card.getComments().stream().map(CommentSnapshot::of).toList(),
                card.getPosition(),
                card.getUpdatedAt(),
                card.getArchivedAt(),
                card.getLaneEnteredAt());
    }

    public boolean hasChecklist() {
        return !checklist.isEmpty();
    }

    public long checklistDone() {
        return checklist.stream().filter(ChecklistItemSnapshot::done).count();
    }

    public boolean isChecklistComplete() {
        return hasChecklist() && checklistDone() == checklist.size();
    }

    public int votes() {
        return voters.size();
    }

    public boolean hasVoteFrom(String name) {
        return voters.stream().anyMatch(voter -> voter.equalsIgnoreCase(name));
    }

    public CardAge age(Instant now) {
        return CardAge.of(laneEnteredAt, now);
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public boolean hasComments() {
        return !comments.isEmpty();
    }

    public boolean hasDescription() {
        return description != null;
    }

    public boolean isOverdue(LocalDate today) {
        return dueDate != null && dueDate.isBefore(today);
    }

    public boolean isDueSoon(LocalDate today) {
        return dueDate != null && !isOverdue(today) && !dueDate.isAfter(today.plusDays(DUE_SOON_DAYS));
    }

    /** Case-insensitive free text match over title, description, assignee and tags. */
    public boolean matches(String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String needle = query.strip().toLowerCase(Locale.ROOT);
        return Stream.concat(Stream.of(title, description, assignee), tags.stream())
                .filter(text -> text != null)
                .anyMatch(text -> text.toLowerCase(Locale.ROOT).contains(needle));
    }

    public boolean isEstimated() {
        return estimate != null;
    }

    public CardDraft toDraft() {
        return new CardDraft(title, description, priority, dueDate, assignee, tags, estimate);
    }
}
