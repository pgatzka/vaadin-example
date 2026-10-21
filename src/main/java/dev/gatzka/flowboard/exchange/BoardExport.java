package dev.gatzka.flowboard.exchange;

import dev.gatzka.flowboard.domain.Priority;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

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

    public static final int CURRENT_VERSION = 1;

    public record LaneExport(String name, Integer wipLimit, boolean done, List<CardExport> cards) {
    }

    public record CardExport(
            String title,
            String description,
            Priority priority,
            LocalDate dueDate,
            String assignee,
            Set<String> tags,
            List<ChecklistItemExport> checklist,
            List<CommentExport> comments) {
    }

    public record ChecklistItemExport(String text, boolean done) {
    }

    public record CommentExport(String author, String text, Instant createdAt) {
    }

    public int cardCount() {
        return lanes.stream().mapToInt(lane -> lane.cards().size()).sum();
    }
}
