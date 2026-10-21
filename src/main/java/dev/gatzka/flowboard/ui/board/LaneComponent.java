package dev.gatzka.flowboard.ui.board;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.badge.BadgeVariant;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.dnd.DragSource;
import com.vaadin.flow.component.dnd.DropEffect;
import com.vaadin.flow.component.dnd.DropTarget;
import com.vaadin.flow.component.dnd.EffectAllowed;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import dev.gatzka.flowboard.domain.CardFilter;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.LaneSnapshot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A lane with its cards. Cards are drag sources; both the cards and the lane body are drop
 * targets: dropping on a card inserts before it, dropping on the body appends.
 */
final class LaneComponent extends Div {

    private final LaneSnapshot lane;
    private final List<CardComponent> cardComponents = new ArrayList<>();
    private final Instant now;

    private final QuickAddComposer composer;

    LaneComponent(LaneSnapshot lane, int laneCount, LocalDate today, Instant now, BoardActions actions) {
        this.now = now;
        this.lane = lane;
        addClassName("lane");
        if (lane.isOverWipLimit()) {
            addClassName("lane--over-limit");
        }
        if (lane.done()) {
            addClassName("lane--done");
        }
        getElement().setAttribute("data-lane-id", Long.toString(lane.id()));

        composer = new QuickAddComposer(
                title -> actions.quickAddCard(lane, title),
                titles -> actions.quickAddCards(lane, titles),
                typed -> actions.newCard(lane, typed),
                () -> actions.composerOpened(lane),
                () -> actions.composerClosed(lane));
        composer.addClassName("lane__add");

        add(header(laneCount, actions), cards(today, actions), composer);
        makeReorderable(actions);
    }

    /** The header drags the lane; dropping anything on the lane outside its card list lands here. */
    private void makeReorderable(BoardActions actions) {
        DropTarget<LaneComponent> target = DropTarget.create(this);
        target.setDropEffect(DropEffect.MOVE);
        target.addDropListener(event -> {
            Object data = event.getDragData().orElse(null);
            if (data instanceof LaneDrag dragged && dragged.laneId() != lane.id()) {
                actions.moveLaneTo(dragged.laneId(), lane.position());
            } else if (data instanceof Long cardId) {
                actions.moveCard(cardId, lane.id(), lane.cards().size());
            }
        });
    }

    /** Expands the quick-add composer, e.g. to restore it after the lane was re-rendered. */
    void openComposer() {
        composer.open();
    }

    LaneSnapshot lane() {
        return lane;
    }

    /** Hides cards that do not match; the lane itself stays visible as a drop target. */
    void filter(CardFilter filter, LocalDate today, String me) {
        for (CardComponent component : cardComponents) {
            component.setVisible(filter.matches(component.card(), lane, today, me));
        }
    }

    private Component header(int laneCount, BoardActions actions) {
        Span name = new Span(lane.name());
        name.addClassName("lane__name");

        Div header = new Div(name);
        if (lane.hasEstimates()) {
            Badge points = new Badge(lane.totalEstimate() + " pt");
            points.addThemeVariants(BadgeVariant.SMALL);
            points.addClassName("lane__points");
            points.getElement().setAttribute("title", "Story points in this lane");
            header.add(points);
        }
        header.add(countBadge(), menu(laneCount, actions));
        header.addClassName("lane__header");
        DragSource<Div> handle = DragSource.create(header);
        handle.setEffectAllowed(EffectAllowed.MOVE);
        handle.setDragData(new LaneDrag(lane.id()));
        handle.addDragStartListener(event -> addClassName("lane--dragging"));
        handle.addDragEndListener(event -> removeClassName("lane--dragging"));
        return header;
    }

    private Badge countBadge() {
        int count = lane.cards().size();
        Badge badge = new Badge(lane.hasWipLimit() ? count + " / " + lane.wipLimit() : Integer.toString(count));
        badge.addThemeVariants(BadgeVariant.SMALL, lane.isOverWipLimit() ? BadgeVariant.ERROR : BadgeVariant.CONTRAST);
        badge.addClassName("lane__count");
        if (lane.hasWipLimit()) {
            badge.getElement().setAttribute("title", lane.isOverWipLimit()
                    ? "Over the work-in-progress limit of " + lane.wipLimit()
                    : "Work-in-progress limit: " + lane.wipLimit());
        }
        return badge;
    }

    private MenuBar menu(int laneCount, BoardActions actions) {
        MenuBar menu = new MenuBar();
        menu.addThemeVariants(MenuBarVariant.LUMO_TERTIARY_INLINE, MenuBarVariant.LUMO_ICON, MenuBarVariant.LUMO_SMALL);
        MenuItem root = menu.addItem(VaadinIcon.ELLIPSIS_DOTS_V.create());
        root.setAriaLabel("Lane actions");
        SubMenu items = root.getSubMenu();
        items.addItem("Edit lane", event -> actions.editLane(lane));
        MenuItem left = items.addItem("Move left", event -> actions.moveLane(lane, -1));
        left.setEnabled(lane.position() > 0);
        MenuItem right = items.addItem("Move right", event -> actions.moveLane(lane, 1));
        right.setEnabled(lane.position() < laneCount - 1);
        MenuItem sortByVotes = items.addItem("Sort by votes", event -> actions.sortLaneByVotes(lane));
        sortByVotes.setEnabled(lane.cards().stream().anyMatch(card -> card.votes() > 0));
        MenuItem archive = items.addItem("Archive all cards", event -> actions.archiveLane(lane));
        archive.setEnabled(!lane.cards().isEmpty());
        MenuItem delete = items.addItem("Delete lane", event -> actions.deleteLane(lane));
        delete.addClassName("danger");
        return menu;
    }

    private Component cards(LocalDate today, BoardActions actions) {
        Div body = new Div();
        body.addClassName("lane__cards");

        DropTarget<Div> appendTarget = DropTarget.create(body);
        appendTarget.setDropEffect(DropEffect.MOVE);
        appendTarget.addDropListener(event -> draggedCardId(event.getDragData().orElse(null))
                .ifPresent(cardId -> actions.moveCard(cardId, lane.id(), lane.cards().size())));

        for (CardSnapshot card : lane.cards()) {
            CardComponent component = new CardComponent(card, today, now, lane.done(), () -> actions.openCard(card),
                    new CardComponent.Voting(card.hasVoteFrom(actions.currentUser()), () -> actions.toggleVote(card)));
            if (card.changedSince(actions.lastVisit())) {
                component.markChanged();
            }
            makeDraggable(component, card);
            makeInsertTarget(component, card, actions);
            cardComponents.add(component);
            body.add(component);
        }
        if (lane.cards().isEmpty()) {
            Span hint = new Span("Drop cards here");
            hint.addClassName("lane__empty");
            body.add(hint);
        }
        return body;
    }

    private static void makeDraggable(CardComponent component, CardSnapshot card) {
        DragSource<CardComponent> source = DragSource.create(component);
        source.setEffectAllowed(EffectAllowed.MOVE);
        source.setDragData(card.id());
    }

    private void makeInsertTarget(CardComponent component, CardSnapshot card, BoardActions actions) {
        DropTarget<CardComponent> target = DropTarget.create(component);
        target.setDropEffect(DropEffect.MOVE);
        target.addDropListener(event -> draggedCardId(event.getDragData().orElse(null))
                .filter(cardId -> cardId != card.id())
                .ifPresent(cardId -> actions.moveCard(cardId, lane.id(), card.position())));
    }

    private static java.util.Optional<Long> draggedCardId(Object dragData) {
        return dragData instanceof Long cardId ? java.util.Optional.of(cardId) : java.util.Optional.empty();
    }
}
