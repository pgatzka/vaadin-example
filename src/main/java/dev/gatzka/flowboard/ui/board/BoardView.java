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
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
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
import jakarta.annotation.security.PermitAll;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.shared.Registration;
import dev.gatzka.flowboard.assistant.AssistantService;
import dev.gatzka.flowboard.domain.ActivityEntry;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.LaneSnapshot;
import dev.gatzka.flowboard.domain.Recurrence;
import dev.gatzka.flowboard.service.ActivityLog;
import dev.gatzka.flowboard.service.AttachmentService;
import dev.gatzka.flowboard.service.BoardChangeBroadcaster;
import dev.gatzka.flowboard.service.BoardChangedEvent;
import dev.gatzka.flowboard.service.BoardService;
import dev.gatzka.flowboard.service.ChecklistService;
import dev.gatzka.flowboard.service.CommentService;
import dev.gatzka.flowboard.service.DependencyService;
import dev.gatzka.flowboard.service.FlowSnapshotService;
import dev.gatzka.flowboard.service.InsightsService;
import dev.gatzka.flowboard.service.NotFoundException;
import dev.gatzka.flowboard.service.Participant;
import dev.gatzka.flowboard.service.PresenceTracker;
import dev.gatzka.flowboard.service.SharingService;
import dev.gatzka.flowboard.service.VisitService;
import dev.gatzka.flowboard.service.VoteService;
import dev.gatzka.flowboard.ui.ProfileChangedEvent;
import dev.gatzka.flowboard.ui.UserSession;
import dev.gatzka.flowboard.ui.assistant.NotesToCardsDialog;
import dev.gatzka.flowboard.ui.assistant.StandupDialog;
import dev.gatzka.flowboard.ui.assistant.SuggestChecklistDialog;
import dev.gatzka.flowboard.ui.boards.BoardsView;
import dev.gatzka.flowboard.ui.calendar.CalendarView;
import dev.gatzka.flowboard.ui.support.Celebration;
import dev.gatzka.flowboard.ui.support.Toasts;
import dev.gatzka.flowboard.ui.support.UiAccess;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * The Kanban board. Renders a {@link BoardSnapshot} and re-renders whenever the board changes,
 * whether through this user or through anybody else (via server push).
 */
@Route("board/:boardId")
@PermitAll
@RouteAlias("board/:boardId/card/:cardId")
public class BoardView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle, BoardActions, CardEditorActions {

    public static final String BOARD_ID = "boardId";
    public static final String CARD_ID = "cardId";

    private final BoardService boardService;
    private final ChecklistService checklistService;
    private final CommentService commentService;
    private final VoteService voteService;
    private final DependencyService dependencyService;
    private final AttachmentService attachmentService;
    private final InsightsService insightsService;
    private final FlowSnapshotService flowSnapshots;
    private final AssistantService assistant;
    private final ExecutorService executor;
    private final ActivityLog activityLog;
    private final BoardChangeBroadcaster broadcaster;
    private final PresenceTracker presence;
    private final SharingService sharing;
    private final VisitService visits;
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
    private Instant lastVisit;
    private CardEditorDialog openEditor;
    private Long openComposerLaneId;
    private Registration changeSubscription;
    private Registration presenceSubscription;
    private Registration profileSubscription;

    public BoardView(BoardService boardService, ChecklistService checklistService, CommentService commentService,
            VoteService voteService, DependencyService dependencyService, AttachmentService attachmentService,
            InsightsService insightsService, FlowSnapshotService flowSnapshots, AssistantService assistant,
            ExecutorService executor, ActivityLog activityLog,
            BoardChangeBroadcaster broadcaster, PresenceTracker presence, SharingService sharing, VisitService visits,
            UserSession session, Clock clock) {
        this.sharing = sharing;
        this.visits = visits;
        this.boardService = boardService;
        this.checklistService = checklistService;
        this.commentService = commentService;
        this.voteService = voteService;
        this.dependencyService = dependencyService;
        this.attachmentService = attachmentService;
        this.insightsService = insightsService;
        this.flowSnapshots = flowSnapshots;
        this.assistant = assistant;
        this.executor = executor;
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

        Button share = new Button(VaadinIcon.SHARE.create(), event -> new ShareDialog(board, sharing, this::actor).open());
        share.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        share.setAriaLabel("Share");
        share.setTooltipText("Public link");
        share.setId("share-board");

        Button addLane = new Button("Add lane", VaadinIcon.PLUS.create(), event -> addLane());
        addLane.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Div toolbar = new Div(titles, search, filters, participants, activity, insights, calendar, archive, share);
        if (assistant.isAvailable()) {
            toolbar.add(assistantMenu());
        }
        toolbar.add(addLane);
        toolbar.addClassName("board-toolbar");
        return toolbar;
    }

    private MenuBar assistantMenu() {
        MenuBar menu = new MenuBar();
        menu.addThemeVariants(MenuBarVariant.LUMO_TERTIARY);
        MenuItem root = menu.addItem(VaadinIcon.MAGIC.create());
        root.add(new Span("Assistant"));
        root.setAriaLabel("Assistant");
        SubMenu items = root.getSubMenu();
        items.addItem("Standup summary", event -> new StandupDialog(board, activityLog.recent(board.id()), assistant, executor).open());
        items.addItem("Cards from notes…", event -> new NotesToCardsDialog(board, assistant, executor, this::createCards).open());
        return menu;
    }

    private void createCards(List<CardDraft> drafts, LaneSnapshot lane) {
        if (drafts.isEmpty() || lane == null) {
            return;
        }
        perform(() -> drafts.forEach(draft -> boardService.createCard(lane.id(), draft, actor())));
        Toasts.success(drafts.size() == 1 ? "1 card created in " + lane.name() : drafts.size() + " cards created in " + lane.name());
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
        lastVisit = visits.visit(session.login(), found.get().id()).orElse(null);
        render(found.get());
        event.getRouteParameters().getLong(CARD_ID).ifPresent(cardId -> findCard(cardId).ifPresentOrElse(
                this::openCard,
                () -> Toasts.error("That card is not on this board any more")));
    }

    /** Path of this board, or of one of its cards when {@code cardId} is given. */
    public static String pathTo(long boardId, Long cardId) {
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
        if (board != null) {
            visits.visit(session.login(), board.id());
        }
    }

    @Override
    public Instant lastVisit() {
        return lastVisit;
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
        Instant now = clock.instant();
        lanes.removeAll();
        laneComponents.clear();
        for (LaneSnapshot lane : snapshot.lanes()) {
            LaneComponent component = new LaneComponent(lane, snapshot.lanes().size(), today, now, this);
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
            insightsDialog.show(insightsService.forBoard(board.id()), flowSnapshots.cumulativeFlow(board.id()));
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
        insightsDialog.show(insightsService.forBoard(board.id()), flowSnapshots.cumulativeFlow(board.id()));
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
                () -> archiveCard(card),
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
        CardEditorDialog.forNewCard(lane, board, initialTitle, (draft, repeats) -> perform(() -> {
            CardSnapshot created = boardService.createCard(lane.id(), draft, actor());
            if (repeats.repeats()) {
                boardService.setRecurrence(created.id(), repeats, actor());
            }
        })).open();
    }

    @Override
    public void quickAddCard(LaneSnapshot lane, String title) {
        perform(() -> boardService.createCard(lane.id(), CardDraft.titled(title), actor()));
    }

    @Override
    public void quickAddCards(LaneSnapshot lane, List<String> titles) {
        perform(() -> titles.forEach(title -> boardService.createCard(lane.id(), CardDraft.titled(title), actor())));
        Toasts.success(titles.size() + " cards added to " + lane.name());
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
        boolean completing = isCompletingMove(cardId, targetLaneId);
        Optional<CardSnapshot> blocked = findCard(cardId).filter(CardSnapshot::isBlocked).filter(card -> completing);
        if (blocked.isPresent()) {
            confirmMovingBlockedCard(blocked.get(), () -> completeMove(cardId, targetLaneId, targetIndex, true));
            return;
        }
        completeMove(cardId, targetLaneId, targetIndex, completing);
    }

    private void completeMove(long cardId, long targetLaneId, int targetIndex, boolean completing) {
        Optional<CardSnapshot> before = findCard(cardId);
        perform(() -> boardService.moveCard(cardId, targetLaneId, targetIndex, actor()));
        Optional<CardSnapshot> after = findCard(cardId);
        if (before.isEmpty() || after.isEmpty() || before.get().laneId() == after.get().laneId()) {
            return;
        }
        CardSnapshot origin = before.get();
        Runnable undo = () -> perform(() -> boardService.moveCard(cardId, origin.laneId(), origin.position(), actor()));
        if (completing) {
            celebrate(origin.title(), undo);
        } else {
            String laneName = board.lane(targetLaneId).map(LaneSnapshot::name).orElse("another lane");
            Toasts.undoable("Moved \"" + origin.title() + "\" to " + laneName, undo);
        }
    }

    private void confirmMovingBlockedCard(CardSnapshot card, Runnable proceed) {
        String waitingFor = card.blockers().stream().filter(blocker -> !blocker.resolved())
                .map(blocker -> "\"" + blocker.title() + "\"").collect(java.util.stream.Collectors.joining(", "));
        ConfirmDialog confirm = new ConfirmDialog("Still blocked", "\"%s\" is waiting for %s. Mark it done anyway?".formatted(card.title(), waitingFor),
                "Move anyway", event -> proceed.run());
        confirm.setCancelable(true);
        confirm.open();
    }

    /** True when the move takes the card from a lane that is not done into one that is. */
    private boolean isCompletingMove(long cardId, long targetLaneId) {
        boolean targetDone = board.lane(targetLaneId).map(LaneSnapshot::done).orElse(false);
        boolean sourceDone = findCard(cardId).flatMap(card -> board.lane(card.laneId())).map(LaneSnapshot::done).orElse(false);
        return targetDone && !sourceDone;
    }

    private void celebrate(String title, Runnable undo) {
        Toasts.undoable("🎉 Done: " + title, undo);
        getUI().ifPresent(Celebration::confetti);
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
    public void moveLaneTo(long laneId, int targetIndex) {
        perform(() -> boardService.moveLane(laneId, targetIndex, actor()));
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
    public CardSnapshot setRecurrence(long cardId, Recurrence recurrence) {
        CardSnapshot updated = boardService.setRecurrence(cardId, recurrence, actor());
        reload();
        return updated;
    }

    @Override
    public CardSnapshot addComment(long cardId, String text) {
        return changeCard(() -> commentService.add(cardId, actor(), text));
    }

    @Override
    public List<ActivityEntry> history(long cardId) {
        return activityLog.recentForCard(cardId);
    }

    @Override
    public CardSnapshot attach(long cardId, String fileName, String contentType, byte[] data) {
        try {
            return changeCard(() -> attachmentService.attach(cardId, fileName, contentType, data, actor()));
        } catch (IllegalArgumentException rejected) {
            Toasts.error(rejected.getMessage());
            return findCard(cardId).orElseThrow();
        }
    }

    @Override
    public CardSnapshot removeAttachment(long attachmentId) {
        return changeCard(() -> attachmentService.remove(attachmentId, actor()));
    }

    @Override
    public Optional<byte[]> attachmentContent(long attachmentId) {
        return attachmentService.content(attachmentId);
    }

    @Override
    public CardSnapshot addBlocker(long cardId, long blockerId) {
        try {
            return changeCard(() -> dependencyService.addBlocker(cardId, blockerId, actor()));
        } catch (IllegalArgumentException rejected) {
            Toasts.error(rejected.getMessage());
            return findCard(cardId).orElseThrow();
        }
    }

    @Override
    public CardSnapshot removeBlocker(long cardId, long blockerId) {
        return changeCard(() -> dependencyService.removeBlocker(cardId, blockerId, actor()));
    }

    @Override
    public boolean assistantAvailable() {
        return assistant.isAvailable();
    }

    @Override
    public void suggestChecklist(CardSnapshot card) {
        new SuggestChecklistDialog(card, board, assistant, executor,
                steps -> perform(() -> steps.forEach(step -> checklistService.addItem(card.id(), step, actor())))).open();
    }

    private CardSnapshot changeCard(Supplier<CardSnapshot> change) {
        CardSnapshot refreshed = change.get();
        reload();
        return refreshed;
    }

    @Override
    public void sortLaneByVotes(LaneSnapshot lane) {
        perform(() -> boardService.sortLaneByVotes(lane.id(), actor()));
    }

    @Override
    public void toggleVote(CardSnapshot card) {
        perform(() -> voteService.toggle(card.id(), actor()));
    }

    @Override
    public String currentUser() {
        return session.getDisplayName();
    }

    @Override
    public void archiveLane(LaneSnapshot lane) {
        List<Long> cardIds = lane.cards().stream().map(CardSnapshot::id).toList();
        perform(() -> {
            int archived = boardService.archiveLane(lane.id(), actor());
            if (archived == 0) {
                Toasts.info("Nothing to archive");
                return;
            }
            Toasts.undoable(archived == 1 ? "1 card archived" : archived + " cards archived",
                    () -> perform(() -> boardService.restoreCards(cardIds, actor())));
        });
    }

    private void archiveCard(CardSnapshot card) {
        perform(() -> boardService.archiveCard(card.id(), actor()));
        Toasts.undoable("Archived \"" + card.title() + "\"", () -> perform(() -> boardService.restoreCard(card.id(), actor())));
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
