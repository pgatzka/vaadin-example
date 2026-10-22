package dev.gatzka.flowboard.exchange;

import dev.gatzka.flowboard.domain.Priority;
import dev.gatzka.flowboard.domain.Recurrence;
import dev.gatzka.flowboard.domain.RuleAction;
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
        List<LaneExport> lanes,
        List<TemplateExport> templates,
        List<ViewExport> views,
        List<MilestoneExport> milestones) {

    public BoardExport {
        lanes = lanes == null ? List.of() : List.copyOf(lanes);
        templates = templates == null ? List.of() : List.copyOf(templates);
        views = views == null ? List.of() : List.copyOf(views);
        milestones = milestones == null ? List.of() : List.copyOf(milestones);
    }

    /** A card template of the board. */
    public record TemplateExport(String name, String title, String description, Priority priority, String assignee,
            Set<String> tags, Integer estimate, List<String> checklist) {

        public TemplateExport {
            tags = tags == null ? Set.of() : Collections.unmodifiableSortedSet(new TreeSet<>(tags));
            checklist = checklist == null ? List.of() : List.copyOf(checklist);
        }
    }

    /** A milestone of the board; cards refer to it by name. */
    public record MilestoneExport(String name, LocalDate targetDate) {
    }

    /** A saved view: a name and the filter in its URL form, e.g. {@code p=high&tag=ops}. */
    public record ViewExport(String name, String filter) {
    }

    /** A lane automation. */
    public record RuleExport(RuleAction action, String argument) {
    }

    public static final int CURRENT_VERSION = 1;

    public record LaneExport(String name, Integer wipLimit, boolean done, List<CardExport> cards, List<RuleExport> rules) {

        public LaneExport {
            cards = cards == null ? List.of() : List.copyOf(cards);
            rules = rules == null ? List.of() : List.copyOf(rules);
        }
    }

    public record CardExport(
            String title,
            String description,
            Priority priority,
            LocalDate startDate,
            LocalDate dueDate,
            String assignee,
            Set<String> tags,
            Integer estimate,
            Recurrence recurrence,
            List<ChecklistItemExport> checklist,
            List<CommentExport> comments,
            String milestone) {

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
