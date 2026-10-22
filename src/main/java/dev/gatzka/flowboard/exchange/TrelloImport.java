package dev.gatzka.flowboard.exchange;

import dev.gatzka.flowboard.domain.Priority;
import dev.gatzka.flowboard.domain.Recurrence;
import dev.gatzka.flowboard.exchange.BoardExport.CardExport;
import dev.gatzka.flowboard.exchange.BoardExport.ChecklistItemExport;
import dev.gatzka.flowboard.exchange.BoardExport.CommentExport;
import dev.gatzka.flowboard.exchange.BoardExport.LaneExport;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * Reads the JSON a Trello board exports ("Print and export" → "Export as JSON") into a {@link BoardExport}. Lists become
 * lanes, labels become tags, the first member becomes the assignee, checklists and comments come along. Archived lists
 * and cards are left behind; a list called "Done" (or similar) becomes the done lane.
 */
final class TrelloImport {

    private static final Pattern DONE_LIST = Pattern.compile("^(done|complete|completed|finished|shipped|released)\\b.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRIORITY_LABEL = Pattern.compile("^(?:priority[:\\s-]*)?(urgent|critical|high|medium|normal|low)$", Pattern.CASE_INSENSITIVE);

    private TrelloImport() {
    }

    /** True when the JSON has the shape of a Trello board export rather than a Flowboard file. */
    static boolean looksLikeTrello(JsonNode root) {
        return root != null && root.isObject() && root.path("lists").isArray() && root.path("cards").isArray() && !root.has("lanes");
    }

    static BoardExport convert(JsonNode root) {
        String name = text(root, "name");
        if (name == null || name.isBlank()) {
            throw new InvalidBoardFileException("The Trello export has no board name");
        }
        Map<String, String> labelNames = new HashMap<>();
        for (JsonNode label : root.path("labels")) {
            labelNames.put(text(label, "id"), labelName(label));
        }
        Map<String, String> memberNames = new HashMap<>();
        for (JsonNode member : root.path("members")) {
            String memberName = text(member, "fullName");
            memberNames.put(text(member, "id"), memberName == null || memberName.isBlank() ? text(member, "username") : memberName);
        }
        Map<String, List<ChecklistItemExport>> checklists = new HashMap<>();
        for (JsonNode checklist : sortedByPosition(root.path("checklists"))) {
            List<ChecklistItemExport> items = checklists.computeIfAbsent(text(checklist, "idCard"), id -> new ArrayList<>());
            for (JsonNode item : sortedByPosition(checklist.path("checkItems"))) {
                items.add(new ChecklistItemExport(text(item, "name"), "complete".equalsIgnoreCase(text(item, "state"))));
            }
        }
        Map<String, List<CommentExport>> comments = new HashMap<>();
        List<JsonNode> actions = new ArrayList<>();
        root.path("actions").forEach(actions::add);
        actions.sort(Comparator.comparing(action -> text(action, "date"), Comparator.nullsFirst(Comparator.naturalOrder())));
        for (JsonNode action : actions) {
            if (!"commentCard".equals(text(action, "type"))) {
                continue;
            }
            String cardId = text(action.path("data").path("card"), "id");
            String author = text(action.path("memberCreator"), "fullName");
            comments.computeIfAbsent(cardId, id -> new ArrayList<>()).add(new CommentExport(
                    author == null ? "Trello" : author, text(action.path("data"), "text"), instant(text(action, "date"))));
        }
        Map<String, List<CardExport>> cardsByList = new HashMap<>();
        for (JsonNode card : sortedByPosition(root.path("cards"))) {
            if (card.path("closed").asBoolean(false)) {
                continue;
            }
            String cardId = text(card, "id");
            Set<String> tags = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            Priority priority = Priority.MEDIUM;
            List<String> labels = new ArrayList<>();
            for (JsonNode labelId : card.path("idLabels")) {
                String label = labelNames.get(labelId.asString());
                if (label != null) {
                    labels.add(label);
                }
            }
            if (labels.isEmpty()) {
                // older exports carry the labels inline on the card instead of referencing the board's list
                for (JsonNode label : card.path("labels")) {
                    labels.add(labelName(label));
                }
            }
            for (String label : labels) {
                Priority fromLabel = priorityOf(label);
                if (fromLabel != null) {
                    priority = fromLabel;
                } else if (label != null && !label.isBlank()) {
                    tags.add(label);
                }
            }
            String assignee = null;
            for (JsonNode memberId : card.path("idMembers")) {
                assignee = memberNames.get(memberId.asString());
                if (assignee != null) {
                    break;
                }
            }
            String description = text(card, "desc");
            cardsByList.computeIfAbsent(text(card, "idList"), id -> new ArrayList<>()).add(new CardExport(
                    text(card, "name"), description == null || description.isBlank() ? null : description, priority,
                    dueDate(text(card, "start")), dueDate(text(card, "due")), assignee, tags, null, Recurrence.NONE,
                    checklists.getOrDefault(cardId, List.of()), comments.getOrDefault(cardId, List.of()), null));
        }
        List<LaneExport> lanes = new ArrayList<>();
        for (JsonNode list : sortedByPosition(root.path("lists"))) {
            if (list.path("closed").asBoolean(false)) {
                continue;
            }
            String listName = text(list, "name");
            boolean done = listName != null && DONE_LIST.matcher(listName.strip()).matches();
            lanes.add(new LaneExport(listName, null, done, cardsByList.getOrDefault(text(list, "id"), List.of()), List.of()));
        }
        if (lanes.isEmpty()) {
            throw new InvalidBoardFileException("The Trello export has no open lists");
        }
        String description = text(root, "desc");
        return new BoardExport(BoardExport.CURRENT_VERSION, name.strip(), description == null || description.isBlank() ? null : description,
                null, lanes, List.of(), List.of(), List.of());
    }

    private static String labelName(JsonNode label) {
        String labelName = text(label, "name");
        return labelName == null || labelName.isBlank() ? text(label, "color") : labelName;
    }

    private static Priority priorityOf(String label) {
        if (label == null) {
            return null;
        }
        var matcher = PRIORITY_LABEL.matcher(label.strip());
        if (!matcher.matches()) {
            return null;
        }
        return switch (matcher.group(1).toLowerCase(Locale.ROOT)) {
            case "urgent", "critical" -> Priority.URGENT;
            case "high" -> Priority.HIGH;
            case "low" -> Priority.LOW;
            default -> Priority.MEDIUM;
        };
    }

    private static List<JsonNode> sortedByPosition(JsonNode array) {
        List<JsonNode> nodes = new ArrayList<>();
        array.forEach(nodes::add);
        nodes.sort(Comparator.comparingDouble(node -> node.path("pos").asDouble(0)));
        return nodes;
    }

    private static LocalDate dueDate(String due) {
        Instant instant = instant(due);
        return instant == null ? null : instant.atZone(ZoneOffset.UTC).toLocalDate();
    }

    private static Instant instant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException invalid) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asString();
    }
}
