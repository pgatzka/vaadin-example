package dev.gatzka.flowboard.ui.boards;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.shared.Registration;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.BoardSummary;
import dev.gatzka.flowboard.domain.Overview;
import dev.gatzka.flowboard.exchange.BoardExchangeService;
import dev.gatzka.flowboard.service.BoardChangeBroadcaster;
import dev.gatzka.flowboard.service.AccessService;
import dev.gatzka.flowboard.service.BoardService;
import dev.gatzka.flowboard.service.OverviewService;
import dev.gatzka.flowboard.service.NotFoundException;
import dev.gatzka.flowboard.ui.NewBoardRequestedEvent;
import dev.gatzka.flowboard.ui.UserSession;
import dev.gatzka.flowboard.ui.board.BoardView;
import dev.gatzka.flowboard.ui.support.Toasts;
import dev.gatzka.flowboard.ui.support.UiAccess;
import dev.gatzka.flowboard.ui.work.CardLocationRow;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Home: what needs attention across all boards, what happened lately, and the boards themselves. */
@Route("")
@PermitAll
public class BoardsView extends VerticalLayout implements HasDynamicTitle {

    private final BoardService boardService;
    private final OverviewService overviewService;
    private final BoardExchangeService exchange;
    private final BoardChangeBroadcaster broadcaster;
    private final UserSession session;
    private final Clock clock;
    private final AccessService access;

    private final Div dashboard = new Div();
    private final Div grid = new Div();
    private Registration changeSubscription;
    private Registration newBoardSubscription;

    public BoardsView(BoardService boardService, OverviewService overviewService, BoardExchangeService exchange,
            BoardChangeBroadcaster broadcaster, AccessService access, UserSession session, Clock clock) {
        this.boardService = boardService;
        this.access = access;
        this.overviewService = overviewService;
        this.exchange = exchange;
        this.broadcaster = broadcaster;
        this.session = session;
        this.clock = clock;

        addClassName("boards-view");
        setSizeFull();

        H2 heading = new H2(getTranslation("nav.home"));
        Span subtitle = new Span(getTranslation("home.subtitle"));
        subtitle.addClassName("page-subtitle");
        Div titles = new Div(heading, subtitle);

        Button newBoard = new Button(getTranslation("command.newBoard"), VaadinIcon.PLUS.create(), event -> openCreateDialog());
        newBoard.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button importBoard = new Button(getTranslation("home.import"), VaadinIcon.UPLOAD.create(), event -> openImportDialog());
        importBoard.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Div actions = new Div(importBoard, newBoard);
        actions.addClassName("page-header__actions");

        Div header = new Div(titles, actions);
        header.addClassName("page-header");

        dashboard.addClassName("dashboard");
        grid.addClassName("board-grid");
        H3 boardsHeading = new H3(getTranslation("nav.boards"));
        boardsHeading.addClassName("dashboard__heading");
        add(header, dashboard, boardsHeading, grid);
        refresh();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        UI ui = attachEvent.getUI();
        changeSubscription = broadcaster.subscribe(event -> UiAccess.run(ui, this::refresh));
        newBoardSubscription = ComponentUtil.addListener(ui, NewBoardRequestedEvent.class, event -> openCreateDialog());
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (changeSubscription != null) {
            changeSubscription.remove();
            changeSubscription = null;
        }
        if (newBoardSubscription != null) {
            newBoardSubscription.remove();
            newBoardSubscription = null;
        }
    }

    private void refresh() {
        renderDashboard(overviewService.overview(access.visibleBoardIds(session.login())));
        grid.removeAll();
        List<BoardSummary> boards = access.visibleBoards(session.login());
        if (boards.isEmpty()) {
            grid.add(emptyState());
            return;
        }
        for (BoardSummary board : boards) {
            grid.add(new BoardTile(board, () -> open(board), () -> openEditDialog(board),
                    () -> new ExportDialog(board, exchange).open(), () -> confirmDelete(board)));
        }
    }

    private void renderDashboard(Overview overview) {
        dashboard.removeAll();
        dashboard.setVisible(!overview.isEmpty());
        if (overview.isEmpty()) {
            return;
        }
        Div tiles = new Div(
                tile(getTranslation("home.openCards"), overview.openCards(), null),
                tile(getTranslation("home.overdue"), overview.overdueCards(), overview.overdueCards() > 0 ? "warn" : "ok"),
                tile(getTranslation("home.dueIn7Days"), overview.dueThisWeek(), null),
                tile(getTranslation("home.doneIn7Days"), overview.completedLast7Days(), null));
        tiles.addClassName("insights__tiles");

        Div attention = section(getTranslation("home.needsAttention"));
        if (overview.needsAttention().isEmpty()) {
            attention.add(hint(getTranslation("home.nothingOverdue")));
        } else {
            LocalDate today = LocalDate.now(clock);
            Div list = new Div();
            list.addClassName("work-list");
            overview.needsAttention().forEach(location -> list.add(new CardLocationRow(location, today)));
            attention.add(list);
        }

        Div activity = section(getTranslation("home.recentActivity"));
        if (overview.recentActivity().isEmpty()) {
            activity.add(hint(getTranslation("home.nothingYet")));
        } else {
            Div list = new Div();
            list.addClassName("dashboard__activity");
            overview.recentActivity().forEach(entry -> list.add(activityRow(entry)));
            activity.add(list);
        }

        Div columns = new Div(attention, activity);
        columns.addClassName("dashboard__columns");
        dashboard.add(tiles, columns);
    }

    private static Div tile(String label, int value, String state) {
        Span valueSpan = new Span(Integer.toString(value));
        valueSpan.addClassName("insights__tile-value");
        Span labelSpan = new Span(label);
        labelSpan.addClassName("insights__tile-label");
        Div tile = new Div(valueSpan, labelSpan);
        tile.addClassName("insights__tile");
        if (state != null) {
            tile.getElement().setAttribute("data-state", state);
        }
        return tile;
    }

    private static Div section(String title) {
        H3 heading = new H3(title);
        heading.addClassName("dashboard__heading");
        Div section = new Div(heading);
        section.addClassName("dashboard__section");
        return section;
    }

    private static Span hint(String text) {
        Span span = new Span(text);
        span.addClassName("insights__hint");
        return span;
    }

    private static Div activityRow(Overview.ActivityLocation location) {
        Span who = new Span(location.entry().actor());
        who.addClassName("dashboard__actor");
        Span what = new Span(" " + location.entry().message());
        RouterLink board = new RouterLink(location.boardName(), BoardView.class,
                new RouteParameters(BoardView.BOARD_ID, Long.toString(location.boardId())));
        board.addClassName("dashboard__board");
        Div row = new Div(who, what, board);
        row.addClassName("dashboard__activity-row");
        return row;
    }

    private Div emptyState() {
        Span title = new Span(getTranslation("home.noBoards"));
        title.addClassName("empty-state__title");
        Span hint = new Span(getTranslation("home.noBoardsHint"));
        Button create = new Button(getTranslation("home.createBoard"), VaadinIcon.PLUS.create(), event -> openCreateDialog());
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Div empty = new Div(title, hint, create);
        empty.addClassName("empty-state");
        return empty;
    }

    @Override
    public String getPageTitle() {
        return getTranslation("nav.home") + " \u00b7 Flowboard";
    }

    private void open(BoardSummary board) {
        getUI().ifPresent(ui -> ui.navigate(BoardView.class, new RouteParameters(BoardView.BOARD_ID, Long.toString(board.id()))));
    }

    private void openCreateDialog() {
        BoardDialog.forNewBoard((draft, template) -> {
            BoardSnapshot created = boardService.createBoard(draft, template, session.getDisplayName());
            refresh();
            getUI().ifPresent(ui -> ui.navigate(BoardView.class, new RouteParameters(BoardView.BOARD_ID, Long.toString(created.id()))));
        }).open();
    }

    private void openImportDialog() {
        new ImportDialog(exchange, session.getDisplayName(), imported -> {
            refresh();
            Toasts.success(getTranslation("home.imported", imported.name()));
            getUI().ifPresent(ui -> ui.navigate(BoardView.class, new RouteParameters(BoardView.BOARD_ID, Long.toString(imported.id()))));
        }).open();
    }

    private void openEditDialog(BoardSummary board) {
        BoardDialog.forBoard(board.toDraft(), draft -> perform(() -> boardService.updateBoard(board.id(), draft, session.getDisplayName()))).open();
    }

    private void confirmDelete(BoardSummary board) {
        ConfirmDialog confirm = new ConfirmDialog(
                getTranslation("home.deleteTitle", board.name()),
                getTranslation("home.deleteText"),
                getTranslation("common.delete"),
                event -> perform(() -> boardService.deleteBoard(board.id(), session.getDisplayName())));
        confirm.setCancelable(true);
        confirm.setConfirmButtonTheme("error primary");
        confirm.open();
    }

    private void perform(Runnable action) {
        try {
            action.run();
        } catch (NotFoundException gone) {
            Toasts.error(getTranslation("home.boardGone"));
        }
        refresh();
    }
}
