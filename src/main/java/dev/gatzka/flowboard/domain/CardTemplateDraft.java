package dev.gatzka.flowboard.domain;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * What a card template consists of: a name to pick it by and the card it produces. The title may
 * be left out, in which case cards made from it take the template's name as title.
 *
 * @param checklist checklist items the new card starts with, in order
 */
public record CardTemplateDraft(
        String name,
        String title,
        String description,
        Priority priority,
        String assignee,
        Set<String> tags,
        Integer estimate,
        List<String> checklist) {

    public static final int MAX_NAME_LENGTH = 60;
    public static final int MAX_CHECKLIST_ITEMS = 30;

    public CardTemplateDraft {
        name = Text.require(name, "Template name");
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Keep the template name under " + MAX_NAME_LENGTH + " characters");
        }
        title = Text.clean(title);
        description = Text.clean(description);
        priority = Objects.requireNonNullElse(priority, Priority.MEDIUM);
        assignee = Text.clean(assignee);
        tags = new CardDraft(name, null, null, null, null, tags, estimate).tags();
        checklist = checklist == null ? List.of() : checklist.stream().map(Text::clean).filter(Objects::nonNull).toList();
        if (checklist.size() > MAX_CHECKLIST_ITEMS) {
            throw new IllegalArgumentException("At most " + MAX_CHECKLIST_ITEMS + " checklist items");
        }
    }

    /** The card this template produces. */
    public CardDraft toCardDraft() {
        return new CardDraft(title == null ? name : title, description, priority, null, assignee, tags, estimate);
    }
}
