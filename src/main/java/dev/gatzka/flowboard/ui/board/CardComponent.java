package dev.gatzka.flowboard.ui.board;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.avatar.AvatarVariant;
import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.badge.BadgeVariant;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import dev.gatzka.flowboard.domain.CardAge;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.Priority;
import dev.gatzka.flowboard.service.Participant;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** A single card on the board. Purely presentational: drag and drop is wired up by the lane. */
final class CardComponent extends Div {

    private static final DateTimeFormatter SAME_YEAR = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter OTHER_YEAR = DateTimeFormatter.ofPattern("MMM d, uuuu");

    private final CardSnapshot card;

    /** @param voted whether the current user has voted for the card */
    record Voting(boolean voted, Runnable onToggle) {
    }

    CardComponent(CardSnapshot card, LocalDate today, Instant now, boolean completed, Runnable onOpen, Voting voting) {
        this.card = card;
        addClassName("task-card");
        CardAge age = card.age(now);
        if (!completed && age.level() == CardAge.Level.STALE) {
            addClassName("task-card--stale");
        }
        getElement().setAttribute("data-priority", card.priority().name().toLowerCase(Locale.ROOT));
        getElement().setAttribute("role", "button");
        getElement().setAttribute("tabindex", "0");
        getElement().setAttribute("aria-label", card.title() + ", " + card.priority().label() + " priority");

        Div title = new Div(card.title());
        title.addClassName("task-card__title");
        add(title, meta(today, completed, age, voting));

        addClickListener(event -> onOpen.run());
        getElement().addEventListener("keydown", event -> onOpen.run())
                .setFilter("event.key === 'Enter' || event.key === ' '");
    }

    CardSnapshot card() {
        return card;
    }

    private Div meta(LocalDate today, boolean completed, CardAge age, Voting voting) {
        Div meta = new Div();
        meta.addClassName("task-card__meta");

        Button vote = new Button((voting.voted() ? VaadinIcon.THUMBS_UP : VaadinIcon.THUMBS_UP_O).create(), event -> voting.onToggle().run());
        vote.setText(card.votes() == 0 ? "" : Integer.toString(card.votes()));
        vote.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
        vote.addClassName("task-card__vote");
        if (voting.voted()) {
            vote.addClassName("task-card__vote--mine");
        }
        vote.setAriaLabel(voting.voted() ? "Withdraw your vote" : "Vote for this card");
        vote.setTooltipText(card.votes() == 0 ? "Vote" : card.votes() + (card.votes() == 1 ? " vote" : " votes"));
        vote.getElement().addEventListener("click", event -> { }).stopPropagation();
        meta.add(vote);

        if (!completed && age.isNoteworthy()) {
            Badge aging = new Badge(age.days() + " d here", VaadinIcon.HOURGLASS.create());
            aging.addThemeVariants(BadgeVariant.SMALL, age.level() == CardAge.Level.STALE ? BadgeVariant.WARNING : BadgeVariant.CONTRAST);
            aging.addClassName("task-card__age");
            aging.getElement().setAttribute("title", "In this lane for " + age.days() + " days");
            meta.add(aging);
        }

        if (card.isBlocked()) {
            Badge blocked = new Badge("Blocked", VaadinIcon.BAN.create());
            blocked.addThemeVariants(BadgeVariant.SMALL, BadgeVariant.ERROR);
            blocked.addClassName("task-card__blocked");
            blocked.getElement().setAttribute("title", "Waiting for: " + card.blockers().stream()
                    .filter(blocker -> !blocker.resolved()).map(blocker -> blocker.title()).collect(java.util.stream.Collectors.joining(", ")));
            meta.add(blocked);
        }
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
        if (card.isEstimated()) {
            Badge points = new Badge(card.estimate() + " pt");
            points.addThemeVariants(BadgeVariant.SMALL);
            points.addClassName("task-card__estimate");
            points.getElement().setAttribute("title", "Estimate");
            meta.add(points);
        }
        if (card.hasChecklist()) {
            Badge checklist = new Badge(card.checklistDone() + "/" + card.checklist().size(), VaadinIcon.CHECK_SQUARE_O.create());
            checklist.addThemeVariants(BadgeVariant.SMALL, card.isChecklistComplete() ? BadgeVariant.SUCCESS : BadgeVariant.CONTRAST);
            checklist.addClassName("task-card__checklist");
            checklist.getElement().setAttribute("title", "Checklist");
            meta.add(checklist);
        }
        if (card.hasComments()) {
            Badge comments = new Badge(Integer.toString(card.comments().size()), VaadinIcon.COMMENT_O.create());
            comments.addThemeVariants(BadgeVariant.SMALL, BadgeVariant.CONTRAST);
            comments.addClassName("task-card__comments");
            comments.getElement().setAttribute("title", "Comments");
            meta.add(comments);
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
