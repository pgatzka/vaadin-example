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
        LocalDate startDate,
        LocalDate dueDate,
        String assignee,
        Set<String> tags,
        Integer estimate,
        Recurrence recurrence,
        int workedMinutes,
        Set<String> voters,
        List<BlockerSnapshot> blockers,
        List<AttachmentSnapshot> attachments,
        List<ChecklistItemSnapshot> checklist,
        List<CommentSnapshot> comments,
        int position,
        Instant updatedAt,
        Instant archivedAt,
        Instant laneEnteredAt,
        Long coverAttachmentId,
        Long milestoneId) {

    public CardSnapshot {
        blockers = List.copyOf(blockers);
        attachments = List.copyOf(attachments);
        checklist = List.copyOf(checklist);
        comments = List.copyOf(comments);
    }

    /** True while at least one blocker is neither done nor archived. */
    public boolean isBlocked() {
        return blockers.stream().anyMatch(blocker -> !blocker.resolved());
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
                card.getStartDate(),
                card.getDueDate(),
                card.getAssignee(),
                Collections.unmodifiableSet(sortedTags),
                card.getEstimate(),
                card.getRecurrence(),
                card.getWorkedMinutes(),
                Set.copyOf(card.getVoters()),
                card.getBlockers().stream().map(BlockerSnapshot::of)
                        .sorted(java.util.Comparator.comparing(BlockerSnapshot::title, String.CASE_INSENSITIVE_ORDER)).toList(),
                card.getAttachments().stream().map(AttachmentSnapshot::of).toList(),
                card.getChecklist().stream().map(ChecklistItemSnapshot::of).toList(),
                card.getComments().stream().map(CommentSnapshot::of).toList(),
                card.getPosition(),
                card.getUpdatedAt(),
                card.getArchivedAt(),
                card.getLaneEnteredAt(),
                card.getCoverAttachmentId(),
                card.getMilestoneId());
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

    /** The image shown on the card: the chosen cover, or else the first image attachment. */
    public java.util.Optional<AttachmentSnapshot> cover() {
        if (coverAttachmentId != null) {
            java.util.Optional<AttachmentSnapshot> chosen = attachments.stream()
                    .filter(a -> a.id() == coverAttachmentId && a.isImage()).findFirst();
            if (chosen.isPresent()) {
                return chosen;
            }
        }
        return attachments.stream().filter(AttachmentSnapshot::isImage).findFirst();
    }

    /** Whether the given attachment is shown as the cover, chosen or by default. */
    public boolean isCover(long attachmentId) {
        return cover().map(c -> c.id() == attachmentId).orElse(false);
    }

    public boolean hasAttachments() {
        return !attachments.isEmpty();
    }

    public boolean hasComments() {
        return !comments.isEmpty();
    }

    public boolean hasDescription() {
        return description != null;
    }

    public boolean hasWork() {
        return workedMinutes > 0;
    }

    public boolean isRecurring() {
        return recurrence != null && recurrence.repeats();
    }

    /** Whether the card was created or edited after the given moment; a {@code null} moment means never. */
    public boolean changedSince(Instant moment) {
        return moment != null && updatedAt != null && updatedAt.isAfter(moment);
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
        return new CardDraft(title, description, priority, startDate, dueDate, assignee, tags, estimate);
    }
}
