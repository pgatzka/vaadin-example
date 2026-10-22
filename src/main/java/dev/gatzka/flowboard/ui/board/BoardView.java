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
import com.vaadin.flow.component.Component;
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
import dev.gatzka.flowboard.domain.BoardSummary;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardFilter;
import dev.gatzka.flowboard.domain.CardFilterCodec;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.DependencyGraph;
import dev.gatzka.flowboard.domain.CardTemplateSnapshot;
import dev.gatzka.flowboard.domain.LaneRuleSnapshot;
import dev.gatzka.flowboard.domain.LaneSnapshot;
import dev.gatzka.flowboard.domain.RuleAction;
import dev.gatzka.flowboard.domain.Priority;
import dev.gatzka.flowboard.domain.QuickAdd;
import dev.gatzka.flowboard.domain.Recurrence;
import dev.gatzka.flowboard.domain.SavedViewSnapshot;
import dev.gatzka.flowboard.domain.TimerSnapshot;
import dev.gatzka.flowboard.domain.WorkEntrySnapshot;
import dev.gatzka.flowboard.security.AccountService;
import dev.gatzka.flowboard.service.AccessService;
import dev.gatzka.flowboard.service.ActivityLog;
import dev.gatzka.flowboard.service.AutomationService;
import dev.gatzka.flowboard.service.AttachmentService;
import dev.gatzka.flowboard.service.BoardChangeBroadcaster;
import dev.gatzka.flowboard.service.BoardChangedEvent;
import dev.gatzka.flowboard.service.BoardService;
import dev.gatzka.flowboard.service.CardTemplateService;
import dev.gatzka.flowboard.service.ChecklistService;
import dev.gatzka.flowboard.service.CommentService;
import dev.gatzka.flowboard.service.DependencyService;
import dev.gatzka.flowboard.service.FlowSnapshotService;
import dev.gatzka.flowboard.service.InsightsService;
import dev.gatzka.flowboard.service.NotFoundException;
import dev.gatzka.flowboard.service.Participant;
import dev.gatzka.flowboard.service.PresenceTracker;
import dev.gatzka.flowboard.service.SavedViewService;
import dev.gatzka.flowboard.service.SharingService;
import dev.gatzka.flowboard.service.TimeService;
import dev.gatzka.flowboard.service.VisitService;
import dev.gatzka.flowboard.service.VoteService;
import dev.gatzka.flowboard.ui.ProfileChangedEvent;
import dev.gatzka.flowboard.ui.TimerChangedEvent;
import dev.gatzka.flowboard.ui.UserSession;
import dev.gatzka.flowboard.ui.assistant.NotesToCardsDialog;
import dev.gatzka.flowboard.ui.assistant.StandupDialog;
import dev.gatzka.flowboard.ui.assistant.SuggestChecklistDialog;
import dev.gatzka.flowboard.ui.boards.BoardsView;
import dev.gatzka.flowboard.ui.calendar.CalendarView;
import dev.gatzka.flowboard.ui.digest.DigestView;
import dev.gatzka.flowboard.ui.support.Celebration;
import dev.gatzka.flowboard.ui.support.Toasts;
import dev.gatzka.flowboard.ui.support.UiAccess;
import dev.gatzka.flowboard.webhook.WebhookDispatcher;
import dev.gatzka.flowboard.webhook.WebhookService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.stream.IntStream;
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
    /** Query parameter carrying an encoded {@link CardFilter}, so a filtered board can be linked to. */
    public static final String FILTER_PARAMETER = "f";

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
    private final WebhookService webhookService;
    private final WebhookDispatcher webhookDispatcher;
    private final SavedViewService savedViews;
    private final TimeService timeService;
    private final CardTemplateService templateService;
    private final AccessService access;
    private final AccountService accounts;
    private final AutomationService automations;
    private final UserSession session;
    private final Clock clock;

    private final H2 heading = new H2();
    private final Span description = new Span();
    private final TextField search = new TextField();
    private final FilterMenu filters = new FilterMenu(filter -> onFilterChanged());
    private final ViewsBar viewsBar = new ViewsBar(new ViewsBar.Actions() {
        @Override
        public void applyView(SavedViewSnapshot view) {
            applyFilterEverywhere(view.filter());
        }

        @Override
        public void saveView(String name) {
            try {
                savedViews.save(board.id(), name, filters.filter(), actor());
                Toasts.success("View \"" + name.strip() + "\" saved");
            } catch (IllegalArgumentException e) {
                Toasts.error(e.getMessage());
            }
            showViews();
        }

        @Override
        public void deleteView(SavedViewSnapshot view) {
            savedViews.delete(view.id(), actor());
            Toasts.info("View \"" + view.name() + "\" deleted");
            showViews();
        }
    });
    private final AvatarGroup participants = new AvatarGroup();
    private final Div lanes = new Div();
    private final ActivityDialog activityDialog = new ActivityDialog();
    private final InsightsDialog insightsDialog;
    private final DependenciesDialog dependenciesDialog = new DependenciesDialog(
            cardId -> findCard(cardId).ifPresent(this::openCard));
    private final ArchiveDialog archiveDialog;
    private final List<LaneComponent> laneComponents = new ArrayList<>();
    private final Set<Long> selection = new LinkedHashSet<>();
    private final SelectionBar selectionBar = new SelectionBar(new SelectionBar.Actions() {
        @Override
        public void moveSelected(LaneSnapshot lane) {
            List<Long> ids = List.copyOf(selection);
            perform(() -> ids.forEach(id -> boardService.moveCard(id, lane.id(), Integer.MAX_VALUE, actor())));
            Toasts.success(ids.size() + (ids.size() == 1 ? " card moved to " : " cards moved to ") + lane.name());
        }

        @Override
        public void assignSelected(String assignee) {
            changeSelected(draft -> new CardDraft(draft.title(), draft.description(), draft.priority(), draft.dueDate(), assignee, draft.tags(), draft.estimate()),
                    "assigned to " + assignee.strip());
        }

        @Override
        public void prioritiseSelected(Priority priority) {
            changeSelected(draft -> new CardDraft(draft.title(), draft.description(), priority, draft.dueDate(), draft.assignee(), draft.tags(), draft.estimate()),
                    "set to " + priority.label().toLowerCase(java.util.Locale.ROOT));
        }

        @Override
        public void tagSelected(String tag) {
            changeSelected(draft -> {
                Set<String> tags = new LinkedHashSet<>(draft.tags());
                tags.add(tag.strip());
                return new CardDraft(draft.title(), draft.description(), draft.priority(), draft.dueDate(), draft.assignee(), tags, draft.estimate());
            }, "tagged " + tag.strip());
        }

        @Override
        public void archiveSelected() {
            List<Long> ids = List.copyOf(selection);
            selection.clear();
            perform(() -> ids.forEach(id -> boardService.archiveCard(id, actor())));
            Toasts.undoable(ids.size() + (ids.size() == 1 ? " card archived" : " cards archived"),
                    () -> perform(() -> boardService.restoreCards(ids, actor())));
        }

        @Override
        public void clearSelection() {
            selection.clear();
            showSelection();
        }
    });

    private BoardSnapshot board;
    private List<CardTemplateSnapshot> boardTemplates = List.of();
    private Instant lastVisit;
    /** Card to focus after the next renders, so keyboard moves keep the keyboard focus. */
    private Long focusCardId;
    /** Own changes render twice (directly and again through the push), so the focus survives both. */
    private int focusRendersLeft;
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
            WebhookService webhookService, WebhookDispatcher webhookDispatcher, SavedViewService savedViews,
            TimeService timeService, CardTemplateService templateService, AccessService access, AccountService accounts,
            AutomationService automations, UserSession session, Clock clock) {
        this.access = access;
        this.automations = automations;
        this.accounts = accounts;
        this.savedViews = savedViews;
        this.timeService = timeService;
        this.templateService = templateService;
        this.sharing = sharing;
        this.visits = visits;
        this.webhookService = webhookService;
        this.webhookDispatcher = webhookDispatcher;
        this.insightsDialog = new InsightsDialog(clock::instant);
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
        add(toolbar(), viewsBar, lanes, selectionBar);
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

        Button addLane = new Button("Add lane", VaadinIcon.PLUS.create(), event -> addLane());
        addLane.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Div toolbar = new Div(titles, search, filters, participants, activity, insights, moreMenu());
        if (assistant.isAvailable()) {
            toolbar.add(assistantMenu());
        }
        toolbar.add(addLane);
        toolbar.addClassName("board-toolbar");
        return toolbar;
    }

    /** The less frequent board actions, kept out of the way behind one button. */
    private MenuBar moreMenu() {
        MenuBar menu = new MenuBar();
        menu.addThemeVariants(MenuBarVariant.LUMO_TERTIARY, MenuBarVariant.LUMO_ICON);
        MenuItem root = menu.addItem(VaadinIcon.ELLIPSIS_DOTS_H.create());
        root.setAriaLabel("More board actions");
        root.setId("board-more");
        SubMenu items = root.getSubMenu();
        items.addItem(entry(VaadinIcon.CALENDAR, "Calendar"), event -> getUI().ifPresent(ui -> ui.navigate(
                CalendarView.class, new RouteParameters(BOARD_ID, Long.toString(board.id())))));
        items.addItem(entry(VaadinIcon.NEWSPAPER, "Weekly digest"), event -> getUI().ifPresent(ui -> ui.navigate(
                DigestView.class, new RouteParameters(BOARD_ID, Long.toString(board.id()))))).setId("board-digest");
        items.addItem(entry(VaadinIcon.ARCHIVE, "Archive"), event -> openArchive()).setId("board-archive");
        items.addItem(entry(VaadinIcon.CLUSTER, "Dependencies"), event -> openDependencies()).setId("board-dependencies");
        items.addItem(entry(VaadinIcon.USERS, "Members…"), event ->
                new MembersDialog(board, access, accounts, session).open()).setId("board-members");
        items.addItem(entry(VaadinIcon.COPY_O, "Card templates…"), event ->
                new TemplatesDialog(board, templateService, this::actor, this::reload).open()).setId("board-templates");
        items.addItem(entry(VaadinIcon.SHARE, "Public link…"), event -> new ShareDialog(board, sharing, this::actor).open()).setId("share-board");
        items.addItem(entry(VaadinIcon.PLUG, "Webhooks…"), event ->
                new WebhooksDialog(board, webhookService, webhookDispatcher, this::actor, clock).open()).setId("board-webhooks");
        return menu;
    }

    private static Component entry(VaadinIcon icon, String text) {
        Span label = new Span(text);
        Div entry = new Div(icon.create(), label);
        entry.addClassName("menu-entry");
        return entry;
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
        Optional<BoardSnapshot> found = event.getRouteParameters().getLong(BOARD_ID).flatMap(boardService::findBoard)
                .filter(snapshot -> access.canAccess(snapshot.id(), session.login()));
        if (found.isEmpty()) {
            Toasts.error("That board does not exist or is not open to you");
            event.forwardTo(BoardsView.class);
            return;
        }
        lastVisit = visits.visit(session.login(), found.get().id()).orElse(null);
        render(found.get());
        event.getLocation().getQueryParameters().getSingleParameter(FILTER_PARAMETER)
                .map(CardFilterCodec::decode).filter(CardFilter::isActive).ifPresent(this::applyFilterEverywhere);
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
        enableArrowKeyNavigation();
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

    /**
     * Arrow keys move the keyboard focus between cards without a server round trip: up and down
     * within a lane, left and right to the card at the same height in the neighbouring lane.
     */
    private void enableArrowKeyNavigation() {
        lanes.getElement().executeJs("""
                const root = this;
                root.addEventListener('keydown', (e) => {
                  if (e.shiftKey || e.altKey || e.ctrlKey || e.metaKey || !e.key.startsWith('Arrow')) return;
                  const card = e.target.closest && e.target.closest('.task-card');
                  if (!card) return;
                  const visible = (lane) => [...lane.querySelectorAll('.task-card')].filter((c) => c.offsetParent !== null);
                  const lane = card.closest('.lane');
                  const cards = visible(lane);
                  const i = cards.indexOf(card);
                  let next = null;
                  if (e.key === 'ArrowDown') next = cards[i + 1];
                  else if (e.key === 'ArrowUp') next = cards[i - 1];
                  else {
                    const all = [...root.querySelectorAll('.lane')];
                    const target = all[all.indexOf(lane) + (e.key === 'ArrowRight' ? 1 : -1)];
                    if (target) { const t = visible(target); next = t[Math.min(i, t.length - 1)]; }
                  }
                  if (next) { e.preventDefault(); next.focus(); }
                });
                """);
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
        boardTemplates = templateService.forBoard(snapshot.id());
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
        filters.setOptions(snapshot.tags(), snapshot.assignees());
        applyFilter();
        showViews();
        showSelection();
        if (focusCardId != null) {
            long wanted = focusCardId;
            laneComponents.stream().flatMap(lane -> lane.cardComponent(wanted).stream()).findFirst().ifPresent(CardComponent::focus);
            if (--focusRendersLeft <= 0) {
                focusCardId = null;
            }
        }
        if (activityDialog.isOpened()) {
            activityDialog.show(activityLog.recent(board.id()));
        }
        if (insightsDialog.isOpened()) {
            showInsights();
        }
        if (dependenciesDialog.isOpened()) {
            dependenciesDialog.show(DependencyGraph.of(board));
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

    /** Sets the filter in the search box and the menu, applies it, and reflects it in the URL and chips. */
    private void applyFilterEverywhere(CardFilter filter) {
        search.setValue(filter.text());
        filters.setFilter(filter);
        onFilterChanged();
    }

    private void onFilterChanged() {
        applyFilter();
        showViews();
        if (board != null && openEditor == null) {
            showPath(currentPath(null));
        }
    }

    private void showViews() {
        if (board != null) {
            viewsBar.show(savedViews.forBoard(board.id()), filters.filter());
        }
    }

    /** Path to this board, with the active filter appended so the address bar stays shareable. */
    private String currentPath(Long cardId) {
        String path = pathTo(board.id(), cardId);
        CardFilter filter = filters.filter();
        return filter.isActive() ? path + "?" + FILTER_PARAMETER + "=" + encodeQuery(CardFilterCodec.encode(filter)) : path;
    }

    private static String encodeQuery(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
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

    private void showInsights() {
        insightsDialog.show(insightsService.forBoard(board.id()), flowSnapshots.cumulativeFlow(board.id()), insightsService.pulse(board.id()));
    }

    private void openArchive() {
        archiveDialog.show(boardService.archivedCards(board.id()));
        archiveDialog.open();
    }

    private void openDependencies() {
        dependenciesDialog.show(DependencyGraph.of(board));
        dependenciesDialog.open();
    }

    private void openInsights() {
        showInsights();
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
                showPath(currentPath(null));
            }
        });
        openEditor = editor;
        editor.open();
        showPath(currentPath(card.id()));
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
    public void quickAddCard(LaneSnapshot lane, String text) {
        perform(() -> createFromShorthand(lane, text));
    }

    @Override
    public void quickAddCards(LaneSnapshot lane, List<String> lines) {
        perform(() -> lines.forEach(line -> createFromShorthand(lane, line)));
        Toasts.success(lines.size() + " cards added to " + lane.name());
    }

    /** Creates a card from quick-add text, honouring the {@link QuickAdd} shorthand. */
    private void createFromShorthand(LaneSnapshot lane, String text) {
        QuickAdd parsed = QuickAdd.parse(text, LocalDate.now(clock));
        CardSnapshot created = boardService.createCard(lane.id(), parsed.draft(), actor());
        if (parsed.recurrence().repeats()) {
            boardService.setRecurrence(created.id(), parsed.recurrence(), actor());
        }
    }

    @Override
    public void moveCardByKeyboard(CardSnapshot card, String arrowKey) {
        Optional<LaneSnapshot> current = board.lane(card.laneId());
        if (current.isEmpty()) {
            return;
        }
        int laneIndex = board.lanes().indexOf(current.get());
        List<CardSnapshot> cards = current.get().cards();
        int index = IntStream.range(0, cards.size()).filter(i -> cards.get(i).id() == card.id()).findFirst().orElse(-1);
        if (index < 0) {
            return;
        }
        focusCardId = card.id();
        focusRendersLeft = 2;
        // moveCard() takes the insertion index in the lane as it is now, i.e. "drop before this slot".
        switch (arrowKey) {
            case "ArrowUp" -> {
                if (index > 0) {
                    moveCard(card.id(), card.laneId(), index - 1);
                }
            }
            case "ArrowDown" -> {
                if (index < cards.size() - 1) {
                    moveCard(card.id(), card.laneId(), index + 2);
                }
            }
            case "ArrowLeft", "ArrowRight" -> {
                int targetLane = laneIndex + (arrowKey.equals("ArrowRight") ? 1 : -1);
                if (targetLane >= 0 && targetLane < board.lanes().size()) {
                    LaneSnapshot target = board.lanes().get(targetLane);
                    moveCard(card.id(), target.id(), Math.min(index, target.cards().size()));
                }
            }
            default -> focusCardId = null;
        }
    }

    @Override
    public List<CardTemplateSnapshot> templates() {
        return boardTemplates;
    }

    @Override
    public void newCardFromTemplate(LaneSnapshot lane, CardTemplateSnapshot template) {
        perform(() -> {
            CardSnapshot created = templateService.instantiate(template.id(), lane.id(), actor());
            Toasts.success("\"" + created.title() + "\" created from " + template.name());
        });
    }

    @Override
    public List<BoardSummary> boardsForTransfer() {
        return access.visibleBoards(session.login());
    }

    @Override
    public Optional<BoardSnapshot> boardFor(long boardId) {
        return boardService.findBoard(boardId);
    }

    @Override
    public CardSnapshot transferCard(long cardId, long targetLaneId, boolean copy) {
        CardSnapshot result = copy ? boardService.copyCard(cardId, targetLaneId, actor()) : boardService.transferCard(cardId, targetLaneId, actor());
        reload();
        return result;
    }

    @Override
    public void saveAsTemplate(long cardId, String name) {
        templateService.saveFromCard(cardId, name, actor());
        reload();
    }

    @Override
    public void toggleSelection(CardSnapshot card) {
        if (!selection.remove(card.id())) {
            selection.add(card.id());
        }
        showSelection();
    }

    @Override
    public boolean isSelected(CardSnapshot card) {
        return selection.contains(card.id());
    }

    @Override
    public void selectAll(LaneSnapshot lane) {
        lane.cards().forEach(card -> selection.add(card.id()));
        showSelection();
    }

    /** Applies a draft change to every selected card, one activity per card. */
    private void changeSelected(java.util.function.UnaryOperator<CardDraft> change, String what) {
        List<CardSnapshot> cards = selection.stream().map(this::findCard).flatMap(Optional::stream).toList();
        perform(() -> cards.forEach(card -> boardService.updateCard(card.id(), change.apply(card.toDraft()), actor())));
        Toasts.success(cards.size() + (cards.size() == 1 ? " card " : " cards ") + what);
    }

    /** Keeps the selection in step with the board: drops cards that left, marks the rest, shows the bar. */
    private void showSelection() {
        if (board == null) {
            return;
        }
        selection.removeIf(id -> findCard(id).isEmpty());
        laneComponents.forEach(lane -> lane.lane().cards().forEach(card ->
                lane.cardComponent(card.id()).ifPresent(component -> component.setSelected(selection.contains(card.id())))));
        selectionBar.show(selection.size(), board, board.assignees(), board.tags());
    }

    @Override
    public void addCardNear(CardSnapshot card) {
        laneComponents.stream().filter(lane -> lane.lane().id() == card.laneId()).findFirst().ifPresent(LaneComponent::openComposer);
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
        LaneDialog.forLane(lane.toDraft(), draft -> perform(() -> boardService.updateLane(lane.id(), draft, actor())), new LaneDialog.Automation() {
            @Override
            public List<LaneRuleSnapshot> rules() {
                return automations.rulesFor(lane.id());
            }

            @Override
            public void add(RuleAction action, String argument) {
                automations.addRule(lane.id(), action, argument, actor());
                reload();
            }

            @Override
            public void remove(LaneRuleSnapshot rule) {
                automations.removeRule(rule.id(), actor());
                reload();
            }
        }).open();
    }

    @Override
    public List<LaneRuleSnapshot> rulesFor(LaneSnapshot lane) {
        return automations.rulesFor(lane.id());
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
    public CardSnapshot logTime(long cardId, int minutes, String note) {
        return changeCard(() -> timeService.log(cardId, session.getDisplayName(), minutes, note, actor()));
    }

    @Override
    public CardSnapshot removeWorkEntry(long entryId) {
        return changeCard(() -> timeService.removeEntry(entryId, actor()));
    }

    @Override
    public void startTimer(long cardId) {
        timeService.start(cardId, session.getDisplayName());
        getUI().ifPresent(ui -> ComponentUtil.fireEvent(ui, new TimerChangedEvent(ui)));
    }

    @Override
    public Optional<CardSnapshot> stopTimer() {
        Optional<CardSnapshot> stopped = timeService.stop(session.getDisplayName(), null);
        if (stopped.isPresent()) {
            reload();
        }
        getUI().ifPresent(ui -> ComponentUtil.fireEvent(ui, new TimerChangedEvent(ui)));
        return stopped;
    }

    @Override
    public Optional<TimerSnapshot> runningTimer() {
        return timeService.running(session.getDisplayName());
    }

    @Override
    public List<WorkEntrySnapshot> workEntries(long cardId) {
        return timeService.entries(cardId);
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
