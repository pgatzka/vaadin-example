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
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.shared.Registration;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.BoardSummary;
import dev.gatzka.flowboard.exchange.BoardExchangeService;
import dev.gatzka.flowboard.service.BoardChangeBroadcaster;
import dev.gatzka.flowboard.service.BoardService;
import dev.gatzka.flowboard.service.NotFoundException;
import dev.gatzka.flowboard.ui.NewBoardRequestedEvent;
import dev.gatzka.flowboard.ui.UserSession;
import dev.gatzka.flowboard.ui.board.BoardView;
import dev.gatzka.flowboard.ui.support.Toasts;
import dev.gatzka.flowboard.ui.support.UiAccess;
import java.util.List;

/** Landing page listing every board. */
@Route("")
@PageTitle("Boards · Flowboard")
public class BoardsView extends VerticalLayout {

    private final BoardService boardService;
    private final BoardExchangeService exchange;
    private final BoardChangeBroadcaster broadcaster;
    private final UserSession session;

    private final Div grid = new Div();
    private Registration changeSubscription;
    private Registration newBoardSubscription;

    public BoardsView(BoardService boardService, BoardExchangeService exchange, BoardChangeBroadcaster broadcaster,
            UserSession session) {
        this.boardService = boardService;
        this.exchange = exchange;
        this.broadcaster = broadcaster;
        this.session = session;

        addClassName("boards-view");
        setSizeFull();

        H2 heading = new H2("Boards");
        Span subtitle = new Span("Pick a board or start a new one. Everything updates live for everyone.");
        subtitle.addClassName("page-subtitle");
        Div titles = new Div(heading, subtitle);

        Button newBoard = new Button("New board", VaadinIcon.PLUS.create(), event -> openCreateDialog());
        newBoard.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button importBoard = new Button("Import", VaadinIcon.UPLOAD.create(), event -> openImportDialog());
        importBoard.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Div actions = new Div(importBoard, newBoard);
        actions.addClassName("page-header__actions");

        Div header = new Div(titles, actions);
        header.addClassName("page-header");

        grid.addClassName("board-grid");
        add(header, grid);
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
        grid.removeAll();
        List<BoardSummary> boards = boardService.listBoards();
        if (boards.isEmpty()) {
            grid.add(emptyState());
            return;
        }
        for (BoardSummary board : boards) {
            grid.add(new BoardTile(board, () -> open(board), () -> openEditDialog(board),
                    () -> new ExportDialog(board, exchange).open(), () -> confirmDelete(board)));
        }
    }

    private Div emptyState() {
        Span title = new Span("No boards yet");
        title.addClassName("empty-state__title");
        Span hint = new Span("Create your first board to start organising work.");
        Button create = new Button("Create a board", VaadinIcon.PLUS.create(), event -> openCreateDialog());
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Div empty = new Div(title, hint, create);
        empty.addClassName("empty-state");
        return empty;
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
            Toasts.success("Imported \"%s\"".formatted(imported.name()));
            getUI().ifPresent(ui -> ui.navigate(BoardView.class, new RouteParameters(BoardView.BOARD_ID, Long.toString(imported.id()))));
        }).open();
    }

    private void openEditDialog(BoardSummary board) {
        BoardDialog.forBoard(board.toDraft(), draft -> perform(() -> boardService.updateBoard(board.id(), draft, session.getDisplayName()))).open();
    }

    private void confirmDelete(BoardSummary board) {
        ConfirmDialog confirm = new ConfirmDialog(
                "Delete \"%s\"?".formatted(board.name()),
                "All of its lanes and cards are removed for everyone. This cannot be undone.",
                "Delete",
                event -> perform(() -> boardService.deleteBoard(board.id(), session.getDisplayName())));
        confirm.setCancelable(true);
        confirm.setConfirmButtonTheme("error primary");
        confirm.open();
    }

    private void perform(Runnable action) {
        try {
            action.run();
        } catch (NotFoundException gone) {
            Toasts.error("That board no longer exists");
        }
        refresh();
    }
}
