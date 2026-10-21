package dev.gatzka.flowboard.ui.calendar;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.shared.Registration;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.LaneSnapshot;
import dev.gatzka.flowboard.service.BoardChangeBroadcaster;
import dev.gatzka.flowboard.service.BoardChangedEvent;
import dev.gatzka.flowboard.service.BoardService;
import dev.gatzka.flowboard.ui.board.BoardView;
import dev.gatzka.flowboard.ui.boards.BoardsView;
import dev.gatzka.flowboard.ui.support.Toasts;
import dev.gatzka.flowboard.ui.support.UiAccess;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** A month calendar of a board's due dates. Clicking a card opens it on the board. */
@Route("board/:boardId/calendar/:month?")
public class CalendarView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

    static final String MONTH = "month";

    private final BoardService boardService;
    private final BoardChangeBroadcaster broadcaster;
    private final Clock clock;

    private final H2 heading = new H2();
    private final Span monthTitle = new Span();
    private final Div grid = new Div();
    private final Span undated = new Span();

    private BoardSnapshot board;
    private CalendarMonth month;
    private Registration changeSubscription;

    public CalendarView(BoardService boardService, BoardChangeBroadcaster broadcaster, Clock clock) {
        this.boardService = boardService;
        this.broadcaster = broadcaster;
        this.clock = clock;

        addClassName("calendar-view");
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        grid.addClassName("calendar");
        undated.addClassName("calendar__undated");
        add(toolbar(), grid, undated);
        setFlexGrow(1, grid);
    }

    private Div toolbar() {
        heading.addClassName("board-toolbar__title");
        Div titles = new Div(heading);
        titles.addClassName("board-toolbar__titles");

        Button previous = new Button(VaadinIcon.ANGLE_LEFT.create(), event -> show(month.month().minusMonths(1)));
        previous.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        previous.setAriaLabel("Previous month");
        Button next = new Button(VaadinIcon.ANGLE_RIGHT.create(), event -> show(month.month().plusMonths(1)));
        next.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        next.setAriaLabel("Next month");
        Button today = new Button("Today", event -> show(YearMonth.now(clock)));
        today.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        monthTitle.addClassName("calendar__month");
        Div navigation = new Div(previous, monthTitle, next, today);
        navigation.addClassName("calendar__navigation");

        Button backToBoard = new Button("Board", VaadinIcon.TASKS.create(), event -> getUI().ifPresent(
                ui -> ui.navigate(BoardView.class, new RouteParameters(BoardView.BOARD_ID, Long.toString(board.id())))));
        backToBoard.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Div toolbar = new Div(titles, navigation, backToBoard);
        toolbar.addClassName("board-toolbar");
        return toolbar;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Optional<BoardSnapshot> found = event.getRouteParameters().getLong(BoardView.BOARD_ID).flatMap(boardService::findBoard);
        if (found.isEmpty()) {
            Toasts.error("That board does not exist");
            event.forwardTo(BoardsView.class);
            return;
        }
        board = found.get();
        YearMonth requested = event.getRouteParameters().get(MONTH).flatMap(CalendarMonth::parse).orElse(YearMonth.now(clock));
        render(requested);
    }

    @Override
    public String getPageTitle() {
        return board == null ? "Flowboard" : board.name() + " calendar · Flowboard";
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        UI ui = attachEvent.getUI();
        changeSubscription = broadcaster.subscribe(event -> {
            if (event.boardId() == board.id()) {
                UiAccess.run(ui, () -> onRemoteChange(event));
            }
        });
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        changeSubscription.remove();
    }

    private void onRemoteChange(BoardChangedEvent event) {
        if (event.kind() == BoardChangedEvent.Kind.DELETED) {
            getUI().ifPresent(ui -> ui.navigate(BoardsView.class));
            return;
        }
        boardService.findBoard(board.id()).ifPresent(snapshot -> {
            board = snapshot;
            render(month.month());
        });
    }

    /** Switches month and reflects it in the address bar. */
    private void show(YearMonth target) {
        render(target);
        getUI().ifPresent(ui -> ui.getPage().getHistory().replaceState(null,
                "board/" + board.id() + "/calendar/" + month.pathSegment()));
    }

    private void render(YearMonth target) {
        month = CalendarMonth.of(target);
        heading.setText(board.name());
        monthTitle.setText(month.title());

        LocalDate today = LocalDate.now(clock);
        Map<Long, LaneSnapshot> lanesById = board.lanes().stream().collect(Collectors.toMap(LaneSnapshot::id, lane -> lane));
        Map<LocalDate, List<CardSnapshot>> cardsByDay = board.lanes().stream()
                .flatMap(lane -> lane.cards().stream())
                .filter(card -> card.dueDate() != null)
                .collect(Collectors.groupingBy(CardSnapshot::dueDate));
        long withoutDueDate = board.cardCount() - cardsByDay.values().stream().mapToLong(List::size).sum();

        grid.removeAll();
        grid.getStyle().set("--calendar-weeks", Integer.toString(month.weeks()));
        for (DayOfWeek weekday : DayOfWeek.values()) {
            Span header = new Span(weekday.getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            header.addClassName("calendar__weekday");
            grid.add(header);
        }
        for (LocalDate day : month.days()) {
            grid.add(cell(day, today, cardsByDay.getOrDefault(day, List.of()), lanesById));
        }
        undated.setText(withoutDueDate == 0 ? "" : withoutDueDate + (withoutDueDate == 1 ? " card has" : " cards have") + " no due date.");
    }

    private Div cell(LocalDate day, LocalDate today, List<CardSnapshot> cards, Map<Long, LaneSnapshot> lanesById) {
        Div cell = new Div();
        cell.addClassName("calendar__day");
        if (!month.contains(day)) {
            cell.addClassName("calendar__day--outside");
        }
        if (day.equals(today)) {
            cell.addClassName("calendar__day--today");
        }
        Span number = new Span(Integer.toString(day.getDayOfMonth()));
        number.addClassName("calendar__number");
        cell.add(number);
        for (CardSnapshot card : cards) {
            cell.add(chip(card, today, lanesById.get(card.laneId())));
        }
        return cell;
    }

    private RouterLink chip(CardSnapshot card, LocalDate today, LaneSnapshot lane) {
        RouterLink chip = new RouterLink(card.title(), BoardView.class,
                new RouteParameters(Map.of(BoardView.BOARD_ID, Long.toString(board.id()), BoardView.CARD_ID, Long.toString(card.id()))));
        chip.addClassName("calendar__card");
        chip.getElement().setAttribute("data-priority", card.priority().name().toLowerCase(Locale.ROOT));
        boolean completed = lane != null && lane.done();
        if (completed) {
            chip.addClassName("calendar__card--done");
        } else if (card.isOverdue(today)) {
            chip.addClassName("calendar__card--overdue");
        }
        chip.getElement().setAttribute("title", card.title() + (lane == null ? "" : " · " + lane.name()));
        return chip;
    }
}
