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
import com.vaadin.flow.shared.Registration;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.LaneSnapshot;
import dev.gatzka.flowboard.service.ActivityLog;
import dev.gatzka.flowboard.service.BoardChangeBroadcaster;
import dev.gatzka.flowboard.service.BoardChangedEvent;
import dev.gatzka.flowboard.service.BoardService;
import dev.gatzka.flowboard.service.ChecklistService;
import dev.gatzka.flowboard.service.NotFoundException;
import dev.gatzka.flowboard.service.Participant;
import dev.gatzka.flowboard.service.PresenceTracker;
import dev.gatzka.flowboard.ui.ProfileChangedEvent;
import dev.gatzka.flowboard.ui.UserSession;
import dev.gatzka.flowboard.ui.boards.BoardsView;
import dev.gatzka.flowboard.ui.support.Toasts;
import dev.gatzka.flowboard.ui.support.UiAccess;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Kanban board. Renders a {@link BoardSnapshot} and re-renders whenever the board changes,
 * whether through this user or through anybody else (via server push).
 */
@Route("board/:boardId")
public class BoardView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle, BoardActions, ChecklistActions {

    public static final String BOARD_ID = "boardId";

    private final BoardService boardService;
    private final ChecklistService checklistService;
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
    private final List<LaneComponent> laneComponents = new ArrayList<>();

    private BoardSnapshot board;
    private Long openComposerLaneId;
    private Registration changeSubscription;
    private Registration presenceSubscription;
    private Registration profileSubscription;

    public BoardView(BoardService boardService, ChecklistService checklistService, ActivityLog activityLog,
            BoardChangeBroadcaster broadcaster, PresenceTracker presence, UserSession session, Clock clock) {
        this.boardService = boardService;
        this.checklistService = checklistService;
        this.activityLog = activityLog;
        this.broadcaster = broadcaster;
        this.presence = presence;
        this.session = session;
        this.clock = clock;

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

        Button addLane = new Button("Add lane", VaadinIcon.PLUS.create(), event -> addLane());
        addLane.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Div toolbar = new Div(titles, search, filters, participants, activity, addLane);
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

    private void openActivity() {
        activityDialog.show(activityLog.recent(board.id()));
        activityDialog.open();
    }

    // --------------------------------------------------------- BoardActions

    @Override
    public void openCard(CardSnapshot card) {
        CardEditorDialog.forCard(card, board, this,
                draft -> perform(() -> boardService.updateCard(card.id(), draft, actor())),
                () -> perform(() -> boardService.deleteCard(card.id(), actor()))).open();
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

    // ----------------------------------------------------- ChecklistActions

    @Override
    public CardSnapshot addItem(long cardId, String text) {
        return checklist(() -> checklistService.addItem(cardId, text, actor()));
    }

    @Override
    public CardSnapshot setDone(long itemId, boolean done) {
        return checklist(() -> checklistService.setDone(itemId, done, actor()));
    }

    @Override
    public CardSnapshot removeItem(long itemId) {
        return checklist(() -> checklistService.removeItem(itemId, actor()));
    }

    private CardSnapshot checklist(java.util.function.Supplier<CardSnapshot> change) {
        CardSnapshot refreshed = change.get();
        reload();
        return refreshed;
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
