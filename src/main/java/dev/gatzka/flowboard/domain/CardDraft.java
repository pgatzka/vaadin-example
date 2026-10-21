package dev.gatzka.flowboard.domain;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Everything a user provides to create or edit a card. Input is normalised on construction. */
public record CardDraft(
        String title,
        String description,
        Priority priority,
        LocalDate dueDate,
        String assignee,
        Set<String> tags) {

    public CardDraft {
        title = Text.require(title, "Card title");
        description = Text.clean(description);
        priority = Objects.requireNonNullElse(priority, Priority.MEDIUM);
        assignee = Text.clean(assignee);
        tags = normaliseTags(tags);
    }

    private static Set<String> normaliseTags(Set<String> tags) {
        if (tags == null) {
            return Set.of();
        }
        TreeSet<String> cleaned = tags.stream()
                .map(Text::clean)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));
        return Collections.unmodifiableSet(cleaned);
    }
}
