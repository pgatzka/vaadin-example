package dev.gatzka.flowboard.ui.board;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.avatar.AvatarVariant;
import com.vaadin.flow.component.badge.Badge;
import java.util.stream.Collectors;
import java.util.List;
import com.vaadin.flow.component.badge.BadgeVariant;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import dev.gatzka.flowboard.domain.AttachmentSnapshot;
import dev.gatzka.flowboard.ui.i18n.I18n;
import dev.gatzka.flowboard.ui.i18n.Labels;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import dev.gatzka.flowboard.domain.CardAge;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.MilestoneSnapshot;
import dev.gatzka.flowboard.domain.Priority;
import dev.gatzka.flowboard.service.Participant;
import dev.gatzka.flowboard.service.TimeService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** A single card on the board. Purely presentational: drag and drop is wired up by the lane. */
public final class CardComponent extends Div {

    private static DateTimeFormatter sameYear() {
        return I18n.formatter("MMM d");
    }

    private static DateTimeFormatter otherYear() {
        return I18n.formatter("MMM d, uuuu");
    }

    private final CardSnapshot card;
    private final Div meta;
    private Div editorsMarker;

    /** @param voted whether the current user has voted for the card */
    record Voting(boolean voted, Runnable onToggle) {
    }

    /** A card that only shows its state: no voting, no opening, no dragging. */
    public static CardComponent readOnly(CardSnapshot card, LocalDate today, Instant now, boolean completed) {
        return new CardComponent(card, today, now, completed, null, null);
    }

    CardComponent(CardSnapshot card, LocalDate today, Instant now, boolean completed, Runnable onOpen, Voting voting) {
        this(card, today, now, completed, onOpen, voting, null);
    }

    /** @param onToggleSelect called instead of {@code onOpen} for clicks with Ctrl, Cmd or Shift held; {@code null} disables selection */
    CardComponent(CardSnapshot card, LocalDate today, Instant now, boolean completed, Runnable onOpen, Voting voting, Runnable onToggleSelect) {
        this.card = card;
        addClassName("task-card");
        boolean readOnly = onOpen == null;
        if (readOnly) {
            addClassName("task-card--readonly");
        }
        CardAge age = card.age(now);
        if (!completed && age.level() == CardAge.Level.STALE) {
            addClassName("task-card--stale");
        }
        getElement().setAttribute("data-priority", card.priority().name().toLowerCase(Locale.ROOT));
        getElement().setAttribute("aria-label", I18n.t("card.ariaPriority", card.title(), Labels.priority(card.priority())));

        Div title = new Div(card.title());
        title.addClassName("task-card__title");
        meta = meta(today, completed, age, voting);
        add(title, meta);

        if (!readOnly) {
            getElement().setAttribute("role", "button");
            getElement().setAttribute("tabindex", "0");
            addClickListener(event -> {
                if (onToggleSelect != null && (event.isCtrlKey() || event.isMetaKey() || event.isShiftKey())) {
                    onToggleSelect.run();
                } else {
                    onOpen.run();
                }
            });
            getElement().addEventListener("keydown", event -> onOpen.run())
                    .setFilter("event.key === 'Enter' || event.key === ' '")
                    .preventDefault();
        }
    }

    /** Wires Shift+arrows to move the card and {@code n} to add a card next to it. */
    void enableKeyboardActions(java.util.function.Consumer<String> onArrow, Runnable onAddNear) {
        getElement().addEventListener("keydown", event -> onArrow.accept(event.getEventData().path("event.key").asString()))
                .setFilter("event.shiftKey && event.key.startsWith('Arrow')")
                .addEventData("event.key")
                .preventDefault()
                .stopPropagation();
        getElement().addEventListener("keydown", event -> onAddNear.run())
                .setFilter("event.key === 'n' && !event.ctrlKey && !event.metaKey && !event.altKey")
                .preventDefault();
    }

    /** Moves keyboard focus to this card in the browser, unless the user is busy in some input. */
    void focus() {
        getElement().executeJs("const a = document.activeElement; if (!a || a === document.body || !document.contains(a)) this.focus()");
    }

    public CardSnapshot card() {
        return card;
    }

    void setSelected(boolean selected) {
        if (selected) {
            addClassName("task-card--selected");
        } else {
            removeClassName("task-card--selected");
        }
        getElement().setAttribute("aria-selected", Boolean.toString(selected));
    }

    /** Shows an image attachment across the top of the card. */
    public void showCover(AttachmentSnapshot cover, java.util.function.LongFunction<java.util.Optional<byte[]>> content) {
        DownloadHandler handler = DownloadHandler.fromInputStream(event -> content.apply(cover.id())
                .map(bytes -> new DownloadResponse(new java.io.ByteArrayInputStream(bytes), cover.fileName(), cover.contentType(), bytes.length))
                .orElseGet(() -> DownloadResponse.error(404)), cover.fileName()).inline();
        Image image = new Image(handler, "");
        image.addClassName("task-card__cover");
        image.getElement().setAttribute("draggable", "false");
        addComponentAsFirst(image);
        addClassName("task-card--with-cover");
    }

    private CardContextMenu contextMenu;

    void setContextMenu(CardContextMenu menu) {
        this.contextMenu = menu;
    }

    /** The right-click menu, or {@code null} on a read-only card. */
    CardContextMenu contextMenu() {
        return contextMenu;
    }

    /** Shows the people who have this card open in their editor right now; an empty list clears the marker. */
    void showEditors(List<Participant> editors) {
        if (editorsMarker != null) {
            remove(editorsMarker);
            editorsMarker = null;
        }
        if (editors.isEmpty()) {
            removeClassName("task-card--editing");
            return;
        }
        Div marker = new Div();
        marker.addClassName("task-card__editors");
        for (Participant person : editors) {
            Avatar avatar = new Avatar(person.name());
            avatar.setColorIndex(person.colorIndex());
            avatar.addThemeVariants(AvatarVariant.LUMO_XSMALL);
            marker.add(avatar);
        }
        String names = editors.stream().map(Participant::name).collect(Collectors.joining(", "));
        marker.getElement().setAttribute("title", I18n.t(editors.size() == 1 ? "presence.editingOne" : "presence.editingMany", names));
        editorsMarker = marker;
        addClassName("task-card--editing");
        add(marker);
    }

    /** Shows which milestone the card counts towards. */
    public void showMilestone(MilestoneSnapshot milestone) {
        Badge badge = new Badge(milestone.name(), VaadinIcon.FLAG_O.create());
        badge.addThemeVariants(BadgeVariant.SMALL, BadgeVariant.CONTRAST);
        badge.addClassName("task-card__milestone");
        badge.getElement().setAttribute("title", milestone.hasTarget()
                ? I18n.t("card.milestoneDue", milestone.name(), I18n.formatter("MMM d, uuuu").format(milestone.targetDate()))
                : I18n.t("card.milestone", milestone.name()));
        meta.addComponentAsFirst(badge);
    }

    /** Flags the card as changed since the viewer last opened the board. */
    void markChanged() {
        addClassName("task-card--changed");
        getElement().setAttribute("title", I18n.t("card.changed"));
    }

    /** Shows the small eye for a card the current user watches. */
    void markWatching() {
        addClassName("task-card--watching");
    }

    private Div meta(LocalDate today, boolean completed, CardAge age, Voting voting) {
        Div meta = new Div();
        meta.addClassName("task-card__meta");

        if (voting != null) {
            meta.add(voteButton(voting));
        } else if (card.votes() > 0) {
            Badge votes = new Badge(Integer.toString(card.votes()), VaadinIcon.THUMBS_UP_O.create());
            votes.addThemeVariants(BadgeVariant.SMALL, BadgeVariant.CONTRAST);
            votes.addClassName("task-card__vote");
            meta.add(votes);
        }

        if (!completed && age.isNoteworthy()) {
            Badge aging = new Badge(I18n.t("card.daysHere", age.days()), VaadinIcon.HOURGLASS.create());
            aging.addThemeVariants(BadgeVariant.SMALL, age.level() == CardAge.Level.STALE ? BadgeVariant.WARNING : BadgeVariant.CONTRAST);
            aging.addClassName("task-card__age");
            aging.getElement().setAttribute("title", I18n.t("card.daysHereTitle", age.days()));
            meta.add(aging);
        }

        if (card.isBlocked()) {
            Badge blocked = new Badge(I18n.t("card.blocked"), VaadinIcon.BAN.create());
            blocked.addThemeVariants(BadgeVariant.SMALL, BadgeVariant.ERROR);
            blocked.addClassName("task-card__blocked");
            blocked.getElement().setAttribute("title", I18n.t("card.waitingFor", card.blockers().stream()
                    .filter(blocker -> !blocker.resolved()).map(blocker -> blocker.title()).collect(java.util.stream.Collectors.joining(", "))));
            meta.add(blocked);
        }
        if (card.priority() == Priority.URGENT || card.priority() == Priority.HIGH) {
            Badge priority = new Badge(Labels.priority(card.priority()));
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
        if (card.hasWork()) {
            Badge time = new Badge(TimeService.humanize(card.workedMinutes()), VaadinIcon.CLOCK.create());
            time.addThemeVariants(BadgeVariant.SMALL, BadgeVariant.CONTRAST);
            time.addClassName("task-card__time");
            time.getElement().setAttribute("title", I18n.t("card.timeLogged"));
            meta.add(time);
        }
        if (card.isRecurring()) {
            Badge repeats = new Badge(VaadinIcon.REFRESH.create());
            repeats.addThemeVariants(BadgeVariant.SMALL, BadgeVariant.CONTRAST, BadgeVariant.ICON_ONLY);
            repeats.addClassName("task-card__repeats");
            repeats.getElement().setAttribute("title", I18n.t("card.repeats", Labels.recurrence(card.recurrence()).toLowerCase(I18n.locale())));
            meta.add(repeats);
        }
        if (card.isEstimated()) {
            Badge points = new Badge(I18n.t("unit.points", card.estimate()));
            points.addThemeVariants(BadgeVariant.SMALL);
            points.addClassName("task-card__estimate");
            points.getElement().setAttribute("title", I18n.t("card.estimate"));
            meta.add(points);
        }
        if (card.hasChecklist()) {
            Badge checklist = new Badge(card.checklistDone() + "/" + card.checklist().size(), VaadinIcon.CHECK_SQUARE_O.create());
            checklist.addThemeVariants(BadgeVariant.SMALL, card.isChecklistComplete() ? BadgeVariant.SUCCESS : BadgeVariant.CONTRAST);
            checklist.addClassName("task-card__checklist");
            checklist.getElement().setAttribute("title", I18n.t("card.checklist"));
            meta.add(checklist);
        }
        if (card.hasAttachments()) {
            Badge files = new Badge(Integer.toString(card.attachments().size()), VaadinIcon.PAPERCLIP.create());
            files.addThemeVariants(BadgeVariant.SMALL, BadgeVariant.CONTRAST);
            files.addClassName("task-card__attachments");
            files.getElement().setAttribute("title", I18n.t("card.attachments"));
            meta.add(files);
        }
        if (card.hasComments()) {
            Badge comments = new Badge(Integer.toString(card.comments().size()), VaadinIcon.COMMENT_O.create());
            comments.addThemeVariants(BadgeVariant.SMALL, BadgeVariant.CONTRAST);
            comments.addClassName("task-card__comments");
            comments.getElement().setAttribute("title", I18n.t("card.comments"));
            meta.add(comments);
        }
        if (card.hasDescription()) {
            Icon notes = VaadinIcon.ALIGN_LEFT.create();
            notes.addClassName("task-card__notes");
            notes.setTooltipText(I18n.t("card.hasDescription"));
            meta.add(notes);
        }
        if (card.assignee() != null) {
            meta.add(assigneeAvatar());
        }
        return meta;
    }

    private Button voteButton(Voting voting) {
        Button vote = new Button((voting.voted() ? VaadinIcon.THUMBS_UP : VaadinIcon.THUMBS_UP_O).create(), event -> voting.onToggle().run());
        vote.setText(card.votes() == 0 ? "" : Integer.toString(card.votes()));
        vote.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
        vote.addClassName("task-card__vote");
        if (voting.voted()) {
            vote.addClassName("task-card__vote--mine");
        }
        vote.setAriaLabel(I18n.t(voting.voted() ? "card.withdrawVote" : "card.vote"));
        vote.setTooltipText(card.votes() == 0 ? I18n.t("card.voteTooltip") : I18n.t("card.votes", card.votes()));
        vote.getElement().addEventListener("click", event -> { }).stopPropagation();
        return vote;
    }

    private Component dueBadge(LocalDate today, boolean completed) {
        LocalDate due = card.dueDate();
        DateTimeFormatter formatter = due.getYear() == today.getYear() ? sameYear() : otherYear();
        Badge badge = new Badge(formatter.format(due), VaadinIcon.CALENDAR_O.create());
        badge.addThemeVariants(BadgeVariant.SMALL);
        badge.addClassName("task-card__due");
        if (completed) {
            badge.addThemeVariants(BadgeVariant.SUCCESS);
            badge.getElement().setAttribute("title", I18n.t("card.completed"));
        } else if (card.isOverdue(today)) {
            badge.addThemeVariants(BadgeVariant.ERROR);
            badge.getElement().setAttribute("title", I18n.t("card.overdue"));
        } else if (card.isDueSoon(today)) {
            badge.addThemeVariants(BadgeVariant.WARNING);
            badge.getElement().setAttribute("title", I18n.t("card.dueSoon"));
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
