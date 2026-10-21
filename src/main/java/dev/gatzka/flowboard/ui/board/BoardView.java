package dev.gatzka.flowboard.ui.board;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.avatar.AvatarGroup;
import com.vaadin.flow.component.avatar.AvatarGroup.AvatarGroupItem;
import com.vaadin.flow.component.avatar.AvatarGroupVariant;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.shared.Registration;
import dev.gatzka.flowboard.domain.ActivityEntry;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.LaneSnapshot;
import dev.gatzka.flowboard.service.ActivityLog;
import dev.gatzka.flowboard.service.BoardChangeBroadcaster;
import dev.gatzka.flowboard.service.BoardChangedEvent;
import dev.gatzka.flowboard.service.BoardService;
import dev.gatzka.flowboard.service.ChecklistService;
import dev.gatzka.flowboard.service.CommentService;
import dev.gatzka.flowboard.service.InsightsService;
import dev.gatzka.flowboard.service.NotFoundException;
import dev.gatzka.flowboard.service.Participant;
import dev.gatzka.flowboard.service.PresenceTracker;
import dev.gatzka.flowboard.ui.ProfileChangedEvent;
import dev.gatzka.flowboard.ui.UserSession;
import dev.gatzka.flowboard.ui.boards.BoardsView;
import dev.gatzka.flowboard.ui.calendar.CalendarView;
import dev.gatzka.flowboard.ui.support.Toasts;
import dev.gatzka.flowboard.ui.support.UiAccess;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The Kanban board. Renders a {@link BoardSnapshot} and re-renders whenever the board changes,
 * whether through this user or through anybody else (via server push).
 */
@Route("board/:boardId")
@RouteAlias("board/:boardId/card/:cardId")
public class BoardView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle, BoardActions, CardEditorActions {

    public static final String BOARD_ID = "boardId";
    public static final String CARD_ID = "cardId";

    private final BoardService boardService;
    private final ChecklistService checklistService;
    private final CommentService commentService;
    private final InsightsService insightsService;
    private final ActivityLog activityLog;
    private final BoardChangeBroadcaster broadcaster;
    private final PresenceTracker presence;
    private final UserSession session;
    private final Clock clock;

    private final H2 heading = new H2();
    private final Span description = new Span();
    private final TextField search = new TextField();
    private final FilterMenu filters = new FilterMenu(filter -> applyFilter());
    private final AvatarGroup participants = new AvatarGroup();
    private final Div lanes = new Div();
    private final ActivityDialog activityDialog = new ActivityDialog();
    private final InsightsDialog insightsDialog = new InsightsDialog();
    private final ArchiveDialog archiveDialog;
    private final List<LaneComponent> laneComponents = new ArrayList<>();

    private BoardSnapshot board;
    private CardEditorDialog openEditor;
    private Long openComposerLaneId;
    private Registration changeSubscription;
    private Registration presenceSubscription;
    private Registration profileSubscription;

    public BoardView(BoardService boardService, ChecklistService checklistService, CommentService commentService,
            InsightsService insightsService, ActivityLog activityLog, BoardChangeBroadcaster broadcaster,
            PresenceTracker presence, UserSession session, Clock clock) {
        this.boardService = boardService;
        this.checklistService = checklistService;
        this.commentService = commentService;
        this.insightsService = insightsService;
        this.activityLog = activityLog;
        this.broadcaster = broadcaster;
        this.presence = presence;
        this.session = session;
        this.clock = clock;
        this.archiveDialog = new ArchiveDialog(
                card -> perform(() -> boardService.restoreCard(card.id(), actor())),
                card -> perform(() -> boardService.deleteCard(card.id(), actor())));

        addClassName("board-view");
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        add(toolbar(), lanes);
        lanes.addClassName("lanes");
        setFlexGrow(1, lanes);

        Shortcuts.addShortcutListener(this, event -> search.focus(), Key.SLASH);
    }

    private Div toolbar() {
        heading.addClassName("board-toolbar__title");
        description.addClassName("board-toolbar__description");
        Div titles = new Div(heading, description);
        titles.addClassName("board-toolbar__titles");

        search.setPlaceholder("Filter cards");
        search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.setTooltipText("Press / to focus");
        search.addClassName("board-toolbar__search");
        search.addValueChangeListener(event -> filters.setQuery(event.getValue()));

        participants.addThemeVariants(AvatarGroupVariant.LUMO_SMALL);
        participants.setMaxItemsVisible(5);
        participants.addClassName("board-toolbar__presence");

        Button activity = new Button(VaadinIcon.CLOCK.create(), event -> openActivity());
        activity.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        activity.setAriaLabel("Show activity");
        activity.setTooltipText("Activity");

        Button insights = new Button(VaadinIcon.CHART.create(), event -> openInsights());
        insights.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        insights.setAriaLabel("Show insights");
        insights.setTooltipText("Insights");

        Button calendar = new Button(VaadinIcon.CALENDAR.create(), event -> getUI().ifPresent(ui -> ui.navigate(
                CalendarView.class, new RouteParameters(BOARD_ID, Long.toString(board.id())))));
        calendar.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        calendar.setAriaLabel("Show calendar");
        calendar.setTooltipText("Calendar");

        Button archive = new Button(VaadinIcon.ARCHIVE.create(), event -> openArchive());
        archive.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        archive.setAriaLabel("Show archive");
        archive.setTooltipText("Archive");

        Button addLane = new Button("Add lane", VaadinIcon.PLUS.create(), event -> addLane());
        addLane.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Div toolbar = new Div(titles, search, filters, participants, activity, insights, calendar, archive, addLane);
        toolbar.addClassName("board-toolbar");
        return toolbar;
    }

    // ------------------------------------------------------------ navigation

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Optional<BoardSnapshot> found = event.getRouteParameters().getLong(BOARD_ID).flatMap(boardService::findBoard);
        if (found.isEmpty()) {
            Toasts.error("That board does not exist");
            event.forwardTo(BoardsView.class);
            return;
        }
        render(found.get());
        event.getRouteParameters().getLong(CARD_ID).ifPresent(cardId -> findCard(cardId).ifPresentOrElse(
                this::openCard,
                () -> Toasts.error("That card is not on this board any more")));
    }

    /** Path of this board, or of one of its cards when {@code cardId} is given. */
    static String pathTo(long boardId, Long cardId) {
        String boardPath = "board/" + boardId;
        return cardId == null ? boardPath : boardPath + "/card/" + cardId;
    }

    @Override
    public String getPageTitle() {
        return board == null ? "Flowboard" : board.name() + " · Flowboard";
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        UI ui = attachEvent.getUI();
        changeSubscription = broadcaster.subscribe(event -> {
            if (event.boardId() == board.id()) {
                UiAccess.run(ui, () -> onRemoteChange(event));
            }
        });
        joinPresence(ui);
        profileSubscription = ComponentUtil.addListener(ui, ProfileChangedEvent.class, event -> {
            leavePresence();
            joinPresence(ui);
        });
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        changeSubscription.remove();
        profileSubscription.remove();
        leavePresence();
    }

    private void joinPresence(UI ui) {
        presenceSubscription = presence.join(board.id(), session.participant(),
                people -> UiAccess.run(ui, () -> showParticipants(people)));
    }

    private void leavePresence() {
        if (presenceSubscription != null) {
            presenceSubscription.remove();
            presenceSubscription = null;
        }
    }

    // ------------------------------------------------------------- rendering

    private void onRemoteChange(BoardChangedEvent event) {
        if (event.kind() == BoardChangedEvent.Kind.DELETED) {
            Toasts.info(event.actor() + " " + event.message());
            getUI().ifPresent(ui -> ui.navigate(BoardsView.class));
            return;
        }
        reload();
        if (!event.actor().equals(session.getDisplayName())) {
            Toasts.info(event.actor() + " " + event.message());
        }
    }

    private void reload() {
        boardService.findBoard(board.id()).ifPresentOrElse(this::render, () -> {
            Toasts.error("This board was deleted");
            getUI().ifPresent(ui -> ui.navigate(BoardsView.class));
        });
    }

    private void render(BoardSnapshot snapshot) {
        board = snapshot;
        heading.setText(snapshot.name());
        description.setText(snapshot.description() == null ? "" : snapshot.description());
        description.setVisible(snapshot.description() != null);

        LocalDate today = LocalDate.now(clock);
        lanes.removeAll();
        laneComponents.clear();
        for (LaneSnapshot lane : snapshot.lanes()) {
            LaneComponent component = new LaneComponent(lane, snapshot.lanes().size(), today, this);
            laneComponents.add(component);
            lanes.add(component);
            if (openComposerLaneId != null && openComposerLaneId == lane.id()) {
                component.openComposer();
            }
        }
        applyFilter();
        if (activityDialog.isOpened()) {
            activityDialog.show(activityLog.recent(board.id()));
        }
        if (insightsDialog.isOpened()) {
            insightsDialog.show(insightsService.forBoard(board.id()));
        }
        if (archiveDialog.isOpened()) {
            archiveDialog.show(boardService.archivedCards(board.id()));
        }
        if (openEditor != null) {
            openEditor.cardId().flatMap(this::findCard).ifPresent(openEditor::refresh);
        }
    }

    private Optional<CardSnapshot> findCard(long cardId) {
        return board.lanes().stream()
                .flatMap(lane -> lane.cards().stream())
                .filter(card -> card.id() == cardId)
                .findFirst();
    }

    private void applyFilter() {
        LocalDate today = LocalDate.now(clock);
        laneComponents.forEach(lane -> lane.filter(filters.filter(), today, session.getDisplayName()));
    }

    private void showParticipants(List<Participant> people) {
        participants.setItems(people.stream()
                .map(person -> {
                    AvatarGroupItem item = new AvatarGroupItem(person.name());
                    item.setColorIndex(person.colorIndex());
                    return item;
                })
                .toList());
    }

    private void openArchive() {
        archiveDialog.show(boardService.archivedCards(board.id()));
        archiveDialog.open();
    }

    private void openInsights() {
        insightsDialog.show(insightsService.forBoard(board.id()));
        insightsDialog.open();
    }

    private void openActivity() {
        activityDialog.show(activityLog.recent(board.id()));
        activityDialog.open();
    }

    // --------------------------------------------------------- BoardActions

    @Override
    public void openCard(CardSnapshot card) {
        CardEditorDialog editor = CardEditorDialog.forCard(card, board, this,
                draft -> perform(() -> boardService.updateCard(card.id(), draft, actor())),
                () -> perform(() -> boardService.archiveCard(card.id(), actor())),
                () -> perform(() -> boardService.deleteCard(card.id(), actor())));
        editor.addOpenedChangeListener(event -> {
            if (!event.isOpened() && openEditor == editor) {
                openEditor = null;
                showPath(pathTo(board.id(), null));
            }
        });
        openEditor = editor;
        editor.open();
        showPath(pathTo(board.id(), card.id()));
    }

    /** Keeps the address bar in sync with the open card without triggering navigation. */
    private void showPath(String path) {
        getUI().ifPresent(ui -> ui.getPage().getHistory().replaceState(null, path));
    }

    @Override
    public void newCard(LaneSnapshot lane, String initialTitle) {
        CardEditorDialog.forNewCard(lane, board, initialTitle,
                draft -> perform(() -> boardService.createCard(lane.id(), draft, actor()))).open();
    }

    @Override
    public void quickAddCard(LaneSnapshot lane, String title) {
        perform(() -> boardService.createCard(lane.id(), CardDraft.titled(title), actor()));
    }

    @Override
    public void composerOpened(LaneSnapshot lane) {
        openComposerLaneId = lane.id();
    }

    @Override
    public void composerClosed(LaneSnapshot lane) {
        if (openComposerLaneId != null && openComposerLaneId == lane.id()) {
            openComposerLaneId = null;
        }
    }

    @Override
    public void moveCard(long cardId, long targetLaneId, int targetIndex) {
        perform(() -> boardService.moveCard(cardId, targetLaneId, targetIndex, actor()));
    }

    @Override
    public void editLane(LaneSnapshot lane) {
        LaneDialog.forLane(lane.toDraft(), draft -> perform(() -> boardService.updateLane(lane.id(), draft, actor()))).open();
    }

    @Override
    public void moveLane(LaneSnapshot lane, int delta) {
        perform(() -> boardService.moveLane(lane.id(), lane.position() + delta, actor()));
    }

    @Override
    public void deleteLane(LaneSnapshot lane) {
        new DeleteLaneDialog(lane, board, target -> perform(() -> {
            if (target == null) {
                boardService.deleteLane(lane.id(), actor());
            } else {
                boardService.deleteLaneMovingCards(lane.id(), target.id(), actor());
            }
        })).open();
    }

    // ---------------------------------------------------- CardEditorActions

    @Override
    public CardSnapshot addChecklistItem(long cardId, String text) {
        return changeCard(() -> checklistService.addItem(cardId, text, actor()));
    }

    @Override
    public CardSnapshot setChecklistItemDone(long itemId, boolean done) {
        return changeCard(() -> checklistService.setDone(itemId, done, actor()));
    }

    @Override
    public CardSnapshot removeChecklistItem(long itemId) {
        return changeCard(() -> checklistService.removeItem(itemId, actor()));
    }

    @Override
    public CardSnapshot addComment(long cardId, String text) {
        return changeCard(() -> commentService.add(cardId, actor(), text));
    }

    @Override
    public List<ActivityEntry> history(long cardId) {
        return activityLog.recentForCard(cardId);
    }

    private CardSnapshot changeCard(Supplier<CardSnapshot> change) {
        CardSnapshot refreshed = change.get();
        reload();
        return refreshed;
    }

    @Override
    public void archiveLane(LaneSnapshot lane) {
        perform(() -> {
            int archived = boardService.archiveLane(lane.id(), actor());
            Toasts.info(archived == 1 ? "1 card archived" : archived + " cards archived");
        });
    }

    private void addLane() {
        LaneDialog.forNewLane(draft -> perform(() -> boardService.addLane(board.id(), draft, actor()))).open();
    }

    /** Runs a mutation and re-renders; a stale reference (deleted elsewhere) is reported, not fatal. */
    private void perform(Runnable action) {
        try {
            action.run();
        } catch (NotFoundException gone) {
            Toasts.error("Somebody else removed that in the meantime");
        }
        reload();
    }

    private String actor() {
        return session.getDisplayName();
    }
}
