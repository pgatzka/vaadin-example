package dev.gatzka.flowboard.exchange;

import dev.gatzka.flowboard.domain.Priority;
import dev.gatzka.flowboard.domain.Recurrence;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The portable, versioned JSON shape of a board. Deliberately independent of the entities so
 * the file format can stay stable while the database model evolves.
 */
public record BoardExport(
        int version,
        String name,
        String description,
        Instant exportedAt,
        List<LaneExport> lanes) {

    public BoardExport {
        lanes = lanes == null ? List.of() : List.copyOf(lanes);
    }

    public static final int CURRENT_VERSION = 1;

    public record LaneExport(String name, Integer wipLimit, boolean done, List<CardExport> cards) {

        public LaneExport {
            cards = cards == null ? List.of() : List.copyOf(cards);
        }
    }

    public record CardExport(
            String title,
            String description,
            Priority priority,
            LocalDate dueDate,
            String assignee,
            Set<String> tags,
            Integer estimate,
            Recurrence recurrence,
            List<ChecklistItemExport> checklist,
            List<CommentExport> comments) {

        public CardExport {
            recurrence = recurrence == null ? Recurrence.NONE : recurrence;
            tags = tags == null ? Set.of() : Collections.unmodifiableSortedSet(new TreeSet<>(tags));
            checklist = checklist == null ? List.of() : List.copyOf(checklist);
            comments = comments == null ? List.of() : List.copyOf(comments);
        }
    }

    public record ChecklistItemExport(String text, boolean done) {
    }

    public record CommentExport(String author, String text, Instant createdAt) {
    }

    public int cardCount() {
        return lanes.stream().mapToInt(lane -> lane.cards().size()).sum();
    }
}
