package dev.gatzka.flowboard.ui.walk;

import dev.gatzka.flowboard.ui.i18n.I18n;
import dev.gatzka.flowboard.ui.i18n.Labels;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.avatar.AvatarVariant;
import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.badge.BadgeVariant;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.markdown.Markdown;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.theme.lumo.Lumo;
import dev.gatzka.flowboard.domain.BlockerSnapshot;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.LaneSnapshot;
import dev.gatzka.flowboard.service.AccessService;
import dev.gatzka.flowboard.service.AttachmentService;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import java.io.ByteArrayInputStream;
import dev.gatzka.flowboard.service.BoardChangeBroadcaster;
import dev.gatzka.flowboard.service.BoardChangedEvent;
import dev.gatzka.flowboard.service.BoardService;
import dev.gatzka.flowboard.service.CommentService;
import dev.gatzka.flowboard.service.Participant;
import dev.gatzka.flowboard.ui.UserSession;
import dev.gatzka.flowboard.ui.board.BoardView;
import dev.gatzka.flowboard.ui.boards.BoardsView;
import dev.gatzka.flowboard.ui.support.Durations;
import dev.gatzka.flowboard.ui.support.Toasts;
import dev.gatzka.flowboard.ui.support.UiAccess;
import jakarta.annotation.security.PermitAll;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Standup mode: one open card at a time, big enough for a shared screen, with arrow keys to walk the board, a clock
 * for how long each card has been talked about, a note box and a button to move the card on. Live like the board.
 */
@Route(value = "board/:" + WalkView.BOARD_ID + "/walk", autoLayout = false)
@PermitAll
public class WalkView extends Div implements BeforeEnterObserver, HasDynamicTitle {

    public static final String BOARD_ID = "boardId";

    /** In which order the cards come up. */
    public enum Order {
        LANE("By lane"),
        PERSON("By person");

        private final String label;

        Order(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** One stop on the walk. */
    record Stop(LaneSnapshot lane, CardSnapshot card) {
    }


    private final BoardService boardService;
    private final CommentService comments;
    private final AttachmentService attachments;
    private final BoardChangeBroadcaster broadcaster;
    private final AccessService access;
    private final UserSession session;
    private final Clock clock;

    private final H2 boardName = new H2();
    private final Select<Order> order = new Select<>();
    private final Checkbox skipDone = new Checkbox(I18n.t("walk.skipDone"), true);
    private final Span progress = new Span();
    private final Span clock_ = new Span("0:00");
    private final Span total = new Span("0:00");
    private final Div progressBar = new Div();
    private final Div slide = new Div();
    private final Div empty = new Div();
    private final Button previous = new Button(VaadinIcon.ARROW_LEFT.create(), event -> step(-1));
    private final Button next = new Button(VaadinIcon.ARROW_RIGHT.create(), event -> step(1));
    private final TextField note = new TextField();
    private final Button advance = new Button();
    private final Anchor openOnBoard = new Anchor();
    private final Anchor exit = new Anchor("", I18n.t("walk.exit"));
    private final Div stage = new Div();
    private final List<Registration> arrowKeys = new ArrayList<>();

    private BoardSnapshot board;
    private List<Stop> stops = List.of();
    private int index;
    private Registration changeSubscription;

    public WalkView(BoardService boardService, CommentService comments, BoardChangeBroadcaster broadcaster, AccessService access,
            AttachmentService attachments, UserSession session, Clock clock) {
        this.boardService = boardService;
        this.attachments = attachments;
        this.comments = comments;
        this.broadcaster = broadcaster;
        this.access = access;
        this.session = session;
        this.clock = clock;
        addClassName("walk");

        H1 brand = new H1("Flowboard");
        brand.addClassName("walk__brand");
        Badge mode = new Badge(I18n.t("walk.standup"), VaadinIcon.MEGAPHONE.create());
        mode.addThemeVariants(BadgeVariant.CONTRAST);
        boardName.addClassName("walk__board");
        order.setId("walk-order");
        order.setItems(Order.values());
        order.setItemLabelGenerator(value -> I18n.t(value == Order.LANE ? "groupBy.LANE" : "groupBy.PERSON"));
        order.setValue(Order.LANE);
        order.addValueChangeListener(event -> rebuild(currentCardId()));
        skipDone.setId("walk-skip-done");
        skipDone.addValueChangeListener(event -> rebuild(currentCardId()));
        exit.setId("walk-exit");
        exit.addClassName("walk__exit");
        exit.getElement().setAttribute("router-link", true);
        Div header = new Div(brand, mode, boardName, new Span(), order, skipDone, exit);
        header.addClassName("walk__header");

        progress.setId("walk-progress");
        progress.addClassName("walk__progress");
        clock_.setId("walk-clock");
        clock_.addClassName("walk__clock");
        clock_.getElement().setAttribute("title", I18n.t("walk.timeOnCard"));
        total.addClassName("walk__total");
        total.getElement().setAttribute("title", I18n.t("walk.timeTotal"));
        Div clocks = new Div(VaadinIcon.CLOCK.create(), clock_, new Span("·"), total);
        clocks.addClassName("walk__clocks");
        progressBar.addClassName("walk__bar");
        Div progressFill = new Div();
        progressFill.addClassName("walk__bar-fill");
        progressBar.add(progressFill);
        Div status = new Div(progress, clocks);
        status.addClassName("walk__status");

        slide.addClassName("walk__slide");
        empty.addClassName("walk__empty");
        empty.add(new H2(I18n.t("walk.emptyTitle")), new Span(I18n.t("walk.emptyText")));
        empty.setVisible(false);

        previous.addThemeVariants(ButtonVariant.LUMO_LARGE, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        previous.setAriaLabel(I18n.t("walk.previous"));
        previous.setId("walk-previous");
        next.addThemeVariants(ButtonVariant.LUMO_LARGE, ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ICON);
        next.setAriaLabel(I18n.t("walk.next"));
        next.setId("walk-next");
        note.setId("walk-note");
        note.setPlaceholder(I18n.t("walk.notePlaceholder"));
        note.setClearButtonVisible(true);
        note.addKeyPressListener(Key.ENTER, event -> addNote());
        advance.setId("walk-advance");
        advance.setIcon(VaadinIcon.ARROW_CIRCLE_RIGHT_O.create());
        advance.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        advance.addClickListener(event -> advanceCard());
        openOnBoard.setId("walk-open");
        openOnBoard.setText(I18n.t("walk.openOnBoard"));
        openOnBoard.addClassName("walk__open");
        Div actions = new Div(previous, note, advance, openOnBoard, next);
        actions.addClassName("walk__actions");

        stage.add(status, progressBar, slide, empty, actions);
        stage.addClassName("walk__stage");
        stage.getElement().setAttribute("tabindex", "0");
        add(header, stage);

        // Arrow keys walk the board while the focus is anywhere on the stage, but not while typing a note.
        arrowsEnabled(true);
        note.addFocusListener(event -> arrowsEnabled(false));
        note.addBlurListener(event -> arrowsEnabled(true));
    }

    private void arrowsEnabled(boolean enabled) {
        if (enabled && arrowKeys.isEmpty()) {
            arrowKeys.add(Shortcuts.addShortcutListener(stage, event -> step(1), Key.ARROW_RIGHT).listenOn(stage));
            arrowKeys.add(Shortcuts.addShortcutListener(stage, event -> step(-1), Key.ARROW_LEFT).listenOn(stage));
        } else if (!enabled) {
            arrowKeys.forEach(Registration::remove);
            arrowKeys.clear();
        }
    }

    public static String pathTo(long boardId) {
        return "board/" + boardId + "/walk";
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Optional<BoardSnapshot> found = event.getRouteParameters().getLong(BOARD_ID).flatMap(boardService::findBoard)
                .filter(snapshot -> access.canAccess(snapshot.id(), session.login()));
        if (found.isEmpty()) {
            Toasts.error(I18n.t("board.notFound"));
            event.forwardTo(BoardsView.class);
            return;
        }
        board = found.get();
        exit.setHref(BoardView.pathTo(board.id(), null));
        rebuild(null);
    }

    @Override
    public String getPageTitle() {
        return (board == null ? I18n.t("walk.standup") : I18n.t("walk.standup") + " \u00b7 " + board.name()) + " \u00b7 Flowboard";
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        UI ui = attachEvent.getUI();
        ui.getElement().getThemeList().set(Lumo.DARK, session.isDarkMode());
        total.getElement().executeJs("""
                const el = this; let s = 0;
                if (el._timer) clearInterval(el._timer);
                el._timer = setInterval(() => { s++; el.textContent = Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0'); }, 1000);
                """);
        restartClock();
        stage.getElement().callJsFunction("focus");
        changeSubscription = broadcaster.subscribe(event -> {
            if (board != null && event.boardId() == board.id()) {
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
            Toasts.info(event.actor() + " " + event.message());
            getUI().ifPresent(ui -> ui.navigate(BoardsView.class));
            return;
        }
        Long current = currentCardId();
        board = boardService.findBoard(board.id()).orElse(board);
        rebuild(current);
    }

    /** The cards to walk through, in the chosen order. Package-private for tests. */
    static List<Stop> stops(BoardSnapshot board, Order order, boolean skipDone) {
        List<Stop> inLaneOrder = new ArrayList<>();
        for (LaneSnapshot lane : board.lanes()) {
            if (skipDone && lane.done()) {
                continue;
            }
            lane.cards().forEach(card -> inLaneOrder.add(new Stop(lane, card)));
        }
        if (order == Order.LANE) {
            return inLaneOrder;
        }
        Map<String, List<Stop>> byPerson = new LinkedHashMap<>();
        inLaneOrder.stream()
                .sorted(Comparator.comparing((Stop stop) -> stop.card().assignee() == null ? 1 : 0)
                        .thenComparing(stop -> stop.card().assignee() == null ? "" : stop.card().assignee().toLowerCase(Locale.ROOT)))
                .forEach(stop -> byPerson.computeIfAbsent(personKey(stop.card()), key -> new ArrayList<>()).add(stop));
        return byPerson.values().stream().flatMap(List::stream).toList();
    }

    private static String personKey(CardSnapshot card) {
        return card.assignee() == null ? "" : card.assignee().strip().toLowerCase(Locale.ROOT);
    }

    private Long currentCardId() {
        return stops.isEmpty() || index >= stops.size() ? null : stops.get(index).card().id();
    }

    private void rebuild(Long keepCardId) {
        if (board == null) {
            return;
        }
        boardName.setText(board.name());
        stops = stops(board, order.getValue(), Boolean.TRUE.equals(skipDone.getValue()));
        int found = -1;
        if (keepCardId != null) {
            for (int i = 0; i < stops.size(); i++) {
                if (stops.get(i).card().id() == keepCardId) {
                    found = i;
                    break;
                }
            }
        }
        index = found >= 0 ? found : Math.min(index, Math.max(0, stops.size() - 1));
        show(false);
    }

    private void step(int delta) {
        if (stops.isEmpty()) {
            return;
        }
        int target = index + delta;
        if (target < 0 || target >= stops.size()) {
            return;
        }
        index = target;
        show(true);
    }

    private void show(boolean restartClock) {
        boolean any = !stops.isEmpty();
        slide.setVisible(any);
        empty.setVisible(!any);
        previous.setEnabled(any && index > 0);
        next.setEnabled(any && index < stops.size() - 1);
        note.setEnabled(any);
        advance.setVisible(false);
        openOnBoard.setVisible(any);
        slide.removeAll();
        if (!any) {
            progress.setText(I18n.t("unit.cards", 0));
            progressBar.getStyle().set("--walk-progress", "0%");
            return;
        }
        Stop stop = stops.get(index);
        progress.setText(I18n.t("walk.progress", index + 1, stops.size()));
        progressBar.getStyle().set("--walk-progress", Math.round(100.0 * (index + 1) / stops.size()) + "%");
        slide.add(slideFor(stop));
        openOnBoard.setHref(BoardView.pathTo(board.id(), stop.card().id()));
        nextLane(stop.lane()).ifPresent(lane -> {
            advance.setText(I18n.t("walk.moveTo", lane.name()));
            advance.setVisible(true);
        });
        if (restartClock) {
            restartClock();
        }
    }

    private void restartClock() {
        clock_.getElement().executeJs("""
                const el = this; let s = 0; el.textContent = '0:00';
                if (el._timer) clearInterval(el._timer);
                el._timer = setInterval(() => { s++; el.textContent = Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0'); }, 1000);
                """);
    }

    private Optional<LaneSnapshot> nextLane(LaneSnapshot lane) {
        List<LaneSnapshot> lanes = board.lanes();
        for (int i = 0; i < lanes.size() - 1; i++) {
            if (lanes.get(i).id() == lane.id()) {
                return Optional.of(lanes.get(i + 1));
            }
        }
        return Optional.empty();
    }

    private Div slideFor(Stop stop) {
        CardSnapshot card = stop.card();
        LocalDate today = LocalDate.now(clock);
        Div who = new Div();
        who.addClassName("walk__who");
        if (card.assignee() != null) {
            Avatar avatar = new Avatar(card.assignee());
            avatar.setColorIndex(Participant.named(card.assignee()).colorIndex());
            avatar.addThemeVariants(AvatarVariant.LUMO_LARGE);
            Span name = new Span(card.assignee());
            name.addClassName("walk__assignee");
            who.add(avatar, name);
        } else {
            Span name = new Span(I18n.t("filter.unassigned"));
            name.addClassName("walk__assignee");
            name.addClassName("walk__assignee--none");
            who.add(VaadinIcon.USER_CLOCK.create(), name);
        }
        Badge lane = new Badge(stop.lane().name());
        lane.addThemeVariants(BadgeVariant.CONTRAST);
        lane.addClassName("walk__lane");
        who.add(lane);

        H1 title = new H1(card.title());
        title.addClassName("walk__title");
        title.getElement().setAttribute("data-priority", card.priority().name().toLowerCase(Locale.ROOT));

        Div facts = new Div();
        facts.addClassName("walk__facts");
        Badge priority = new Badge(Labels.priority(card.priority()));
        priority.addClassName("walk__priority");
        priority.getElement().setAttribute("data-priority", card.priority().name().toLowerCase(Locale.ROOT));
        facts.add(priority);
        if (card.dueDate() != null) {
            String when = I18n.formatter("EEE, MMM d").format(card.dueDate());
            Badge due = new Badge(I18n.t(card.isOverdue(today) ? "walk.overdue" : "walk.due", when), VaadinIcon.CALENDAR_O.create());
            if (card.isOverdue(today)) {
                due.addThemeVariants(BadgeVariant.ERROR);
            } else if (card.isDueSoon(today)) {
                due.addThemeVariants(BadgeVariant.WARNING);
            }
            facts.add(due);
        }
        if (card.isEstimated()) {
            facts.add(new Badge(I18n.t("unit.points", card.estimate())));
        }
        if (card.hasChecklist()) {
            Badge checklist = new Badge(I18n.t("walk.checklist", card.checklistDone(), card.checklist().size()), VaadinIcon.CHECK_SQUARE_O.create());
            if (card.checklistDone() == card.checklist().size()) {
                checklist.addThemeVariants(BadgeVariant.SUCCESS);
            }
            facts.add(checklist);
        }
        card.tags().forEach(tag -> {
            Badge chip = new Badge(tag);
            chip.addThemeVariants(BadgeVariant.CONTRAST);
            facts.add(chip);
        });
        Badge age = new Badge(I18n.t("walk.inLaneFor", stop.lane().name(), Durations.since(card.laneEnteredAt(), clock.instant())), VaadinIcon.HOURGLASS.create());
        age.addThemeVariants(BadgeVariant.CONTRAST);
        facts.add(age);

        Div content = new Div(who, title, facts);
        content.addClassName("walk__content");
        card.cover().ifPresent(cover -> {
            DownloadHandler handler = DownloadHandler.fromInputStream(event -> attachments.content(cover.id())
                    .map(bytes -> new DownloadResponse(new ByteArrayInputStream(bytes), cover.fileName(), cover.contentType(), bytes.length))
                    .orElseGet(() -> DownloadResponse.error(404)), cover.fileName()).inline();
            Image image = new Image(handler, "");
            image.addClassName("walk__cover");
            content.addComponentAsFirst(image);
        });
        if (card.isBlocked()) {
            Div blocked = new Div(VaadinIcon.BAN.create(), new Span(I18n.t("walk.blockedBy", card.blockers().stream()
                    .filter(blocker -> !blocker.resolved()).map(BlockerSnapshot::title).map(t -> "\"" + t + "\"")
                    .reduce((a, b) -> a + ", " + b).orElse(""))));
            blocked.addClassName("walk__blocked");
            content.add(blocked);
        }
        if (card.description() != null && !card.description().isBlank()) {
            Markdown description = new Markdown(card.description());
            description.addClassName("walk__description");
            content.add(description);
        }
        if (!card.comments().isEmpty()) {
            var last = card.comments().getLast();
            Div comment = new Div(new Span(last.author() + ", " + Durations.ago(last.createdAt(), clock.instant()) + ": "), new Span(last.text()));
            comment.addClassName("walk__comment");
            content.add(comment);
        }
        return content;
    }

    private void addNote() {
        String text = note.getValue() == null ? "" : note.getValue().strip();
        if (text.isEmpty() || stops.isEmpty()) {
            return;
        }
        comments.add(stops.get(index).card().id(), session.getDisplayName(), text);
        note.clear();
        Toasts.success(I18n.t("walk.noteAdded"));
    }

    private void advanceCard() {
        if (stops.isEmpty()) {
            return;
        }
        Stop stop = stops.get(index);
        nextLane(stop.lane()).ifPresent(lane -> {
            boardService.moveCard(stop.card().id(), lane.id(), Integer.MAX_VALUE, session.getDisplayName());
            Toasts.success(I18n.t("board.movedTo", stop.card().title(), lane.name()));
        });
    }
}
