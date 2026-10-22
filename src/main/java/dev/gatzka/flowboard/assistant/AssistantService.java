package dev.gatzka.flowboard.assistant;

import dev.gatzka.flowboard.domain.ActivityEntry;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.WeeklyDigest;
import dev.gatzka.flowboard.domain.LaneSnapshot;
import dev.gatzka.flowboard.domain.Priority;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The things the assistant can do for a board. Prompts are built here from snapshots, answers are
 * parsed defensively: the model is asked for JSON where structure matters and the result is
 * validated before anything reaches the domain.
 */
@Service
public class AssistantService {

    static final int MAX_SUGGESTED_ITEMS = 10;
    static final int MAX_EXTRACTED_CARDS = 25;

    private final ObjectProvider<AssistantClient> client;
    private final JsonMapper json;
    private final Clock clock;

    AssistantService(ObjectProvider<AssistantClient> client, JsonMapper json, Clock clock) {
        this.client = client;
        this.json = json;
        this.clock = clock;
    }

    /** False when no API key is configured; the UI hides every assistant action then. */
    public boolean isAvailable() {
        return client.getIfAvailable() != null;
    }

    /** Proposes checklist items for a card, based on its title, description and board context. */
    public List<String> suggestChecklist(CardSnapshot card, BoardSnapshot board) {
        String system = """
                You help a team break Kanban cards into concrete, checkable steps.
                Answer with a JSON array of 3 to %d short strings (each under 80 characters), nothing else.
                Steps must be specific to the card, in a sensible order, and must not repeat existing items.
                """.formatted(MAX_SUGGESTED_ITEMS);
        StringBuilder user = new StringBuilder();
        user.append("Board: ").append(board.name()).append('\n');
        user.append("Card title: ").append(card.title()).append('\n');
        if (card.hasDescription()) {
            user.append("Description:\n").append(card.description()).append('\n');
        }
        if (card.hasChecklist()) {
            user.append("Existing checklist items: ")
                    .append(card.checklist().stream().map(item -> item.text()).collect(Collectors.joining("; ")))
                    .append('\n');
        }
        JsonNode array = parseArray(ask(system, user.toString()));
        List<String> steps = new ArrayList<>();
        for (JsonNode node : array) {
            String step = node.isTextual() ? node.asString().strip() : "";
            if (!step.isEmpty() && steps.size() < MAX_SUGGESTED_ITEMS) {
                steps.add(step.length() > 200 ? step.substring(0, 200) : step);
            }
        }
        if (steps.isEmpty()) {
            throw new AssistantException("The assistant did not come up with any steps for this card.");
        }
        return List.copyOf(steps);
    }

    /** Writes a short standup style status of the board in Markdown. */
    public String standupSummary(BoardSnapshot board, List<ActivityEntry> recentActivity) {
        LocalDate today = LocalDate.now(clock);
        Instant yesterday = clock.instant().minus(Duration.ofHours(24));
        String system = """
                You write crisp daily standup notes for a team working on a Kanban board.
                Use Markdown with these headings: Done in the last 24 hours, In progress, At risk, Suggested focus.
                Mention people by name, call out overdue cards and lanes over their WIP limit, and keep it under 220 words.
                Do not invent anything that is not in the data.
                """;
        StringBuilder user = new StringBuilder();
        user.append("Today: ").append(today).append('\n');
        user.append("Board: ").append(board.name()).append('\n');
        for (LaneSnapshot lane : board.lanes()) {
            user.append("\nLane \"").append(lane.name()).append('"');
            if (lane.done()) {
                user.append(" (done lane)");
            }
            if (lane.isOverWipLimit()) {
                user.append(" (OVER WIP LIMIT ").append(lane.wipLimit()).append(')');
            }
            user.append(":\n");
            if (lane.cards().isEmpty()) {
                user.append("  - empty\n");
            }
            for (CardSnapshot card : lane.cards()) {
                user.append("  - ").append(card.title());
                if (card.assignee() != null) {
                    user.append(" [").append(card.assignee()).append(']');
                }
                if (card.dueDate() != null) {
                    user.append(" due ").append(card.dueDate());
                    if (!lane.done() && card.isOverdue(today)) {
                        user.append(" (OVERDUE)");
                    }
                }
                user.append(" priority ").append(card.priority().label().toLowerCase(Locale.ROOT));
                if (card.hasChecklist()) {
                    user.append(" checklist ").append(card.checklistDone()).append('/').append(card.checklist().size());
                }
                user.append('\n');
            }
        }
        user.append("\nActivity in the last 24 hours:\n");
        List<ActivityEntry> recent = recentActivity.stream().filter(entry -> entry.occurredAt().isAfter(yesterday)).toList();
        if (recent.isEmpty()) {
            user.append("  - nothing\n");
        }
        for (ActivityEntry entry : recent) {
            user.append("  - ").append(entry.actor()).append(' ').append(entry.message()).append('\n');
        }
        return ask(system, user.toString()).strip();
    }

    /** Turns free text (meeting notes, an email, a brain dump) into card drafts. */
    /** A short narrative of a week on the board, written from the digest's numbers and lists. */
    public String digestNarrative(WeeklyDigest digest) {
        String system = """
                You write the short narrative paragraph at the top of a team's weekly report about their Kanban board.
                Write 3 to 5 sentences of plain prose in Markdown, no headings and no lists. Name people and cards,
                point out what got done, what is late or blocked, and what next week looks like. Be concrete and warm,
                never invent anything that is not in the data.
                """;
        return ask(system, digest.toMarkdown()).strip();
    }

    public List<CardDraft> cardsFromNotes(String notes, BoardSnapshot board) {
        if (notes == null || notes.isBlank()) {
            return List.of();
        }
        String system = """
                You extract actionable work items from notes for a Kanban board.
                Answer with a JSON array of objects with keys "title" (required, under 100 characters),
                "description" (optional, Markdown, one or two sentences), "priority" (one of low, medium, high, urgent)
                and "assignee" (optional, only when the notes clearly name a person responsible). Nothing else.
                Return at most %d items. Merge duplicates. Skip anything that is not a task.
                """.formatted(MAX_EXTRACTED_CARDS);
        String user = "Board: " + board.name() + "\nKnown people: " + String.join(", ", board.assignees()) + "\nNotes:\n" + notes;
        JsonNode array = parseArray(ask(system, user));
        List<CardDraft> drafts = new ArrayList<>();
        for (JsonNode node : array) {
            if (!node.isObject()) {
                continue;
            }
            String title = text(node, "title").orElse("");
            if (title.isBlank() || drafts.size() >= MAX_EXTRACTED_CARDS) {
                continue;
            }
            Priority priority = text(node, "priority").flatMap(AssistantService::priority).orElse(Priority.MEDIUM);
            drafts.add(new CardDraft(title.length() > 120 ? title.substring(0, 120) : title,
                    text(node, "description").orElse(null), priority, null, text(node, "assignee").orElse(null), Set.of(), null));
        }
        return List.copyOf(drafts);
    }

    private String ask(String system, String user) {
        AssistantClient assistant = client.getIfAvailable();
        if (assistant == null) {
            throw new AssistantException("The assistant is not configured. Set ANTHROPIC_API_KEY to enable it.");
        }
        return Objects.requireNonNullElse(assistant.complete(system, user), "");
    }

    /** Finds the JSON array in an answer, tolerating prose or code fences around it. */
    JsonNode parseArray(String answer) {
        int start = answer.indexOf('[');
        int end = answer.lastIndexOf(']');
        if (start < 0 || end <= start) {
            throw new AssistantException("The assistant answered in an unexpected format.");
        }
        try {
            JsonNode node = json.readTree(answer.substring(start, end + 1));
            if (!node.isArray()) {
                throw new AssistantException("The assistant answered in an unexpected format.");
            }
            return node;
        } catch (JacksonException malformed) {
            throw new AssistantException("The assistant answered in an unexpected format.", malformed);
        }
    }

    private static Optional<String> text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asString().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.asString().strip());
    }

    private static Optional<Priority> priority(String text) {
        try {
            return Optional.of(Priority.valueOf(text.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }
}
