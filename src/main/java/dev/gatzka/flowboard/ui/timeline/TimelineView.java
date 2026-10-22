package dev.gatzka.flowboard.ui.timeline;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.shared.Registration;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.Timeline;
import dev.gatzka.flowboard.domain.Timeline.Bar;
import dev.gatzka.flowboard.domain.Timeline.Group;
import dev.gatzka.flowboard.domain.Timeline.GroupBy;
import dev.gatzka.flowboard.service.AccessService;
import dev.gatzka.flowboard.service.BoardChangeBroadcaster;
import dev.gatzka.flowboard.service.BoardService;
import dev.gatzka.flowboard.service.NotFoundException;
import dev.gatzka.flowboard.ui.UserSession;
import dev.gatzka.flowboard.ui.board.BoardView;
import dev.gatzka.flowboard.ui.boards.BoardsView;
import dev.gatzka.flowboard.ui.support.Toasts;
import dev.gatzka.flowboard.ui.support.UiAccess;
import jakarta.annotation.security.PermitAll;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The board on a time axis: a bar per dated card, grouped by lane or person. Bars can be dragged to reschedule and
 * their ends pulled to change the start or due date; a click opens the card.
 */
@Route("board/:" + BoardView.BOARD_ID + "/timeline")
@PermitAll
public class TimelineView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

    static final int DAY_WIDTH = 22;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d");

    private final BoardService boardService;
    private final BoardChangeBroadcaster broadcaster;
    private final AccessService access;
    private final UserSession session;
    private final Clock clock;

    private final H2 heading = new H2();
    private final Span range = new Span();
    private final Select<GroupBy> groupBy = new Select<>();
    private final Div scroller = new Div();
    private final Span footer = new Span();

    private BoardSnapshot board;
    private LocalDate from;
    private Timeline timeline;
    private Registration changeSubscription;

    public TimelineView(BoardService boardService, BoardChangeBroadcaster broadcaster, AccessService access, UserSession session,
            Clock clock) {
        this.boardService = boardService;
        this.broadcaster = broadcaster;
        this.access = access;
        this.session = session;
        this.clock = clock;
        addClassName("timeline-view");
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        scroller.addClassName("timeline");
        scroller.getStyle().set("--day-width", DAY_WIDTH + "px");
        footer.addClassName("timeline__footer");
        add(toolbar(), scroller, footer);
    }

    private Div toolbar() {
        heading.addClassName("board-toolbar__title");
        Div titles = new Div(heading);
        titles.addClassName("board-toolbar__titles");

        Button previous = new Button(VaadinIcon.ANGLE_LEFT.create(), event -> show(from.minusWeeks(4)));
        previous.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        previous.setAriaLabel("Four weeks back");
        previous.setId("timeline-prev");
        Button next = new Button(VaadinIcon.ANGLE_RIGHT.create(), event -> show(from.plusWeeks(4)));
        next.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        next.setAriaLabel("Four weeks ahead");
        next.setId("timeline-next");
        Button today = new Button("Today", event -> show(Timeline.windowStart(LocalDate.now(clock))));
        today.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        today.setId("timeline-today");
        range.addClassName("calendar__month");
        Div navigation = new Div(previous, range, next, today);
        navigation.addClassName("calendar__navigation");

        groupBy.setId("timeline-group");
        groupBy.setItems(GroupBy.values());
        groupBy.setItemLabelGenerator(GroupBy::label);
        groupBy.setValue(GroupBy.LANE);
        groupBy.addValueChangeListener(event -> render());

        Button backToBoard = new Button("Board", VaadinIcon.TASKS.create(), event -> getUI().ifPresent(
                ui -> ui.navigate(BoardView.class, new RouteParameters(BoardView.BOARD_ID, Long.toString(board.id())))));
        backToBoard.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Div toolbar = new Div(titles, navigation, groupBy, backToBoard);
        toolbar.addClassName("board-toolbar");
        return toolbar;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Optional<BoardSnapshot> found = event.getRouteParameters().getLong(BoardView.BOARD_ID).flatMap(boardService::findBoard)
                .filter(snapshot -> access.canAccess(snapshot.id(), session.login()));
        if (found.isEmpty()) {
            Toasts.error("That board does not exist or is not open to you");
            event.forwardTo(BoardsView.class);
            return;
        }
        board = found.get();
        show(Timeline.windowStart(LocalDate.now(clock)));
    }

    @Override
    public String getPageTitle() {
        return (board == null ? "Timeline" : "Timeline · " + board.name()) + " · Flowboard";
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        UI ui = attachEvent.getUI();
        changeSubscription = broadcaster.subscribe(event -> {
            if (board != null && event.boardId() == board.id()) {
                UiAccess.run(ui, () -> boardService.findBoard(board.id()).ifPresentOrElse(fresh -> {
                    board = fresh;
                    render();
                }, () -> ui.navigate(BoardsView.class)));
            }
        });
        enableDragging();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        changeSubscription.remove();
    }

    private void show(LocalDate windowStart) {
        from = windowStart;
        render();
    }

    /** The current timeline; package-private for tests. */
    Timeline timeline() {
        return timeline;
    }

    private void render() {
        heading.setText(board.name());
        timeline = Timeline.of(board, groupBy.getValue(), from, Timeline.DEFAULT_WEEKS);
        range.setText(DAY.format(timeline.from()) + " – " + DAY.format(timeline.to()));
        LocalDate today = LocalDate.now(clock);
        scroller.removeAll();
        scroller.add(header(today));
        for (Group group : timeline.groups()) {
            Div label = new Div(new Span(group.name()));
            label.addClassName("timeline__group");
            label.getElement().setAttribute("data-group", group.name());
            scroller.add(label);
            group.bars().forEach(bar -> scroller.add(row(bar, today)));
        }
        int todayOffset = timeline.offsetOf(today);
        if (todayOffset >= 0) {
            Div marker = new Div();
            marker.addClassName("timeline__today");
            marker.getStyle().set("--day-offset", Integer.toString(todayOffset));
            scroller.add(marker);
        }
        if (timeline.isEmpty()) {
            Span empty = new Span("No card has a start or due date in these weeks. Give cards dates, or move the window.");
            empty.addClassName("timeline__empty");
            scroller.add(empty);
        }
        StringBuilder note = new StringBuilder();
        if (timeline.undated() > 0) {
            note.append(timeline.undated()).append(timeline.undated() == 1 ? " card has" : " cards have").append(" no dates and stay off the timeline.");
        }
        if (timeline.outside() > 0) {
            note.append(note.isEmpty() ? "" : " ").append(timeline.outside()).append(timeline.outside() == 1 ? " card falls" : " cards fall")
                    .append(" outside these weeks.");
        }
        note.append(note.isEmpty() ? "" : " ").append("Drag a bar to move it, pull its ends to change the dates, click it to open the card.");
        footer.setText(note.toString());
    }

    private Div header(LocalDate today) {
        Div corner = new Div();
        corner.addClassName("timeline__label");
        corner.addClassName("timeline__label--head");
        Div track = new Div();
        track.addClassName("timeline__track");
        track.addClassName("timeline__track--head");
        track.getStyle().set("width", (timeline.days() * DAY_WIDTH) + "px");
        for (LocalDate weekStart : timeline.weekStarts()) {
            Span week = new Span(Timeline.monthLabel(weekStart) + " " + weekStart.getDayOfMonth());
            week.addClassName("timeline__week");
            if (!weekStart.isAfter(today) && today.isBefore(weekStart.plusWeeks(1))) {
                week.addClassName("timeline__week--current");
            }
            week.getStyle().set("width", (7 * DAY_WIDTH) + "px");
            track.add(week);
        }
        Div row = new Div(corner, track);
        row.addClassName("timeline__row");
        row.addClassName("timeline__row--head");
        return row;
    }

    private Div row(Bar bar, LocalDate today) {
        CardSnapshot card = bar.card();
        Span title = new Span(card.title());
        title.addClassName("timeline__title");
        Span meta = new Span(card.assignee() == null ? bar.lane() : card.assignee() + " · " + bar.lane());
        meta.addClassName("timeline__meta");
        Div label = new Div(title, meta);
        label.addClassName("timeline__label");
        label.getElement().setAttribute("data-priority", card.priority().name().toLowerCase(Locale.ROOT));

        Div track = new Div();
        track.addClassName("timeline__track");
        track.getStyle().set("width", (timeline.days() * DAY_WIDTH) + "px");
        Div element = new Div();
        element.addClassName("timeline__bar");
        element.getElement().setAttribute("data-card-id", Long.toString(card.id()));
        element.getElement().setAttribute("data-priority", card.priority().name().toLowerCase(Locale.ROOT));
        element.getElement().setAttribute("title", describe(bar));
        element.getElement().setAttribute("data-title", card.title());
        if (bar.milestone()) {
            element.addClassName("timeline__bar--milestone");
        }
        if (bar.openEnded()) {
            element.addClassName("timeline__bar--open");
        }
        if (bar.done()) {
            element.addClassName("timeline__bar--done");
        }
        if (bar.isOverdue(today)) {
            element.addClassName("timeline__bar--overdue");
        }
        element.getStyle().set("left", (bar.offsetDays(timeline.from()) * DAY_WIDTH) + "px")
                .set("width", (bar.lengthDays() * DAY_WIDTH) + "px");
        Div startHandle = new Div();
        startHandle.addClassName("timeline__handle");
        startHandle.addClassName("timeline__handle--start");
        Div endHandle = new Div();
        endHandle.addClassName("timeline__handle");
        endHandle.addClassName("timeline__handle--end");
        Span text = new Span(bar.milestone() ? "" : card.title());
        text.addClassName("timeline__bar-text");
        element.add(startHandle, text, endHandle);
        track.add(element);

        Div row = new Div(label, track);
        row.addClassName("timeline__row");
        return row;
    }

    private static String describe(Bar bar) {
        CardSnapshot card = bar.card();
        if (bar.milestone()) {
            return card.title() + " · due " + DAY.format(card.dueDate());
        }
        if (bar.openEnded()) {
            return card.title() + " · from " + DAY.format(card.startDate()) + ", no due date";
        }
        return card.title() + " · " + DAY.format(card.startDate()) + " – " + DAY.format(card.dueDate());
    }

    /** Pointer handling lives in the browser: bars move while dragging and the server hears the result once. */
    private void enableDragging() {
        scroller.getElement().executeJs("""
                const root = this; const dayWidth = $0; const view = $1;
                let drag = null;
                root.addEventListener('pointerdown', (e) => {
                  const bar = e.target.closest('.timeline__bar');
                  if (!bar || e.button !== 0) return;
                  const mode = e.target.classList.contains('timeline__handle--start') ? 'start'
                      : e.target.classList.contains('timeline__handle--end') ? 'end' : 'move';
                  drag = { bar, mode, x: e.clientX, left: bar.offsetLeft, width: bar.offsetWidth, moved: false };
                  bar.setPointerCapture(e.pointerId);
                  bar.classList.add('timeline__bar--dragging');
                  e.preventDefault();
                });
                root.addEventListener('pointermove', (e) => {
                  if (!drag) return;
                  const dx = e.clientX - drag.x;
                  if (Math.abs(dx) > 3) drag.moved = true;
                  if (drag.mode === 'move') drag.bar.style.left = (drag.left + dx) + 'px';
                  else if (drag.mode === 'end') drag.bar.style.width = Math.max(dayWidth, drag.width + dx) + 'px';
                  else { const w = Math.max(dayWidth, drag.width - dx); drag.bar.style.left = (drag.left + drag.width - w) + 'px'; drag.bar.style.width = w + 'px'; }
                });
                const finish = (e) => {
                  if (!drag) return;
                  const current = drag; drag = null;
                  current.bar.classList.remove('timeline__bar--dragging');
                  const dx = e.clientX - current.x;
                  const days = Math.round(dx / dayWidth);
                  if (!current.moved) { view.$server.open(current.bar.dataset.cardId); return; }
                  if (days === 0) { current.bar.style.left = current.left + 'px'; current.bar.style.width = current.width + 'px'; return; }
                  view.$server.reschedule(current.bar.dataset.cardId, current.mode, days);
                };
                root.addEventListener('pointerup', finish);
                root.addEventListener('pointercancel', finish);
                """, DAY_WIDTH, getElement());
    }

    @ClientCallable
    void open(String cardId) {
        getUI().ifPresent(ui -> ui.navigate(BoardView.class, new RouteParameters(Map.of(
                BoardView.BOARD_ID, Long.toString(board.id()), BoardView.CARD_ID, cardId))));
    }

    /**
     * Applies a drag: {@code move} shifts both dates, {@code start} and {@code end} one of them. A milestone that gets
     * its start pulled gains a start date; an open-ended bar that gets its end pulled gains a due date.
     */
    @ClientCallable
    void reschedule(String cardId, String mode, int days) {
        long id = Long.parseLong(cardId);
        Optional<CardSnapshot> found = board.lanes().stream().flatMap(lane -> lane.cards().stream()).filter(card -> card.id() == id).findFirst();
        if (found.isEmpty() || days == 0) {
            render();
            return;
        }
        CardSnapshot card = found.get();
        LocalDate start = card.startDate();
        LocalDate due = card.dueDate();
        switch (mode) {
            case "start" -> start = (start == null ? due : start).plusDays(days);
            case "end" -> due = (due == null ? timeline.to() : due).plusDays(days);
            default -> {
                start = start == null ? null : start.plusDays(days);
                due = due == null ? null : due.plusDays(days);
            }
        }
        if (start != null && due != null && start.isAfter(due)) {
            if ("start".equals(mode)) {
                start = due;
            } else {
                due = start;
            }
        }
        try {
            CardDraft draft = card.toDraft().withDates(start, due);
            boardService.updateCard(id, draft, session.getDisplayName());
            Toasts.success("\"" + card.title() + "\" now " + describeDates(start, due));
        } catch (NotFoundException gone) {
            Toasts.error("Somebody else removed that card in the meantime");
        } catch (IllegalArgumentException invalid) {
            Toasts.error(invalid.getMessage());
            render();
        }
    }

    private static String describeDates(LocalDate start, LocalDate due) {
        if (start != null && due != null) {
            return "runs " + DAY.format(start) + " – " + DAY.format(due);
        }
        return due != null ? "due " + DAY.format(due) : "starts " + DAY.format(start);
    }
}
