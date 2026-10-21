package dev.gatzka.flowboard.ui.board;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.avatar.AvatarVariant;
import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.badge.BadgeVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.Priority;
import dev.gatzka.flowboard.service.Participant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** A single card on the board. Purely presentational: drag and drop is wired up by the lane. */
final class CardComponent extends Div {

    private static final DateTimeFormatter SAME_YEAR = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter OTHER_YEAR = DateTimeFormatter.ofPattern("MMM d, uuuu");

    private final CardSnapshot card;

    CardComponent(CardSnapshot card, LocalDate today, boolean completed, Runnable onOpen) {
        this.card = card;
        addClassName("task-card");
        getElement().setAttribute("data-priority", card.priority().name().toLowerCase(Locale.ROOT));
        getElement().setAttribute("role", "button");
        getElement().setAttribute("tabindex", "0");
        getElement().setAttribute("aria-label", card.title() + ", " + card.priority().label() + " priority");

        Div title = new Div(card.title());
        title.addClassName("task-card__title");
        add(title, meta(today, completed));

        addClickListener(event -> onOpen.run());
        getElement().addEventListener("keydown", event -> onOpen.run())
                .setFilter("event.key === 'Enter' || event.key === ' '");
    }

    CardSnapshot card() {
        return card;
    }

    private Div meta(LocalDate today, boolean completed) {
        Div meta = new Div();
        meta.addClassName("task-card__meta");

        if (card.priority() == Priority.URGENT || card.priority() == Priority.HIGH) {
            Badge priority = new Badge(card.priority().label());
            priority.addThemeVariants(BadgeVariant.SMALL, card.priority() == Priority.URGENT ? BadgeVariant.ERROR : BadgeVariant.WARNING);
            meta.add(priority);
        }
        if (card.dueDate() != null) {
            meta.add(dueBadge(today, completed));
        }
        for (String tag : card.tags()) {
            Badge badge = new Badge(tag);
            badge.addThemeVariants(BadgeVariant.SMALL, BadgeVariant.CONTRAST);
            badge.addClassName("task-card__tag");
            meta.add(badge);
        }
        if (card.hasDescription()) {
            Icon notes = VaadinIcon.ALIGN_LEFT.create();
            notes.addClassName("task-card__notes");
            notes.setTooltipText("Has a description");
            meta.add(notes);
        }
        if (card.assignee() != null) {
            meta.add(assigneeAvatar());
        }
        return meta;
    }

    private Component dueBadge(LocalDate today, boolean completed) {
        LocalDate due = card.dueDate();
        DateTimeFormatter formatter = due.getYear() == today.getYear() ? SAME_YEAR : OTHER_YEAR;
        Badge badge = new Badge(formatter.format(due), VaadinIcon.CALENDAR_O.create());
        badge.addThemeVariants(BadgeVariant.SMALL);
        badge.addClassName("task-card__due");
        if (completed) {
            badge.addThemeVariants(BadgeVariant.SUCCESS);
            badge.getElement().setAttribute("title", "Completed");
        } else if (card.isOverdue(today)) {
            badge.addThemeVariants(BadgeVariant.ERROR);
            badge.getElement().setAttribute("title", "Overdue");
        } else if (card.isDueSoon(today)) {
            badge.addThemeVariants(BadgeVariant.WARNING);
            badge.getElement().setAttribute("title", "Due soon");
        }
        return badge;
    }

    private Avatar assigneeAvatar() {
        Avatar avatar = new Avatar(card.assignee());
        avatar.setColorIndex(Participant.named(card.assignee()).colorIndex());
        avatar.addThemeVariants(AvatarVariant.LUMO_XSMALL);
        avatar.addClassName("task-card__assignee");
        avatar.setTooltipEnabled(true);
        return avatar;
    }
}
