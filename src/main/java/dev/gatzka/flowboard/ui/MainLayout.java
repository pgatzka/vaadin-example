package dev.gatzka.flowboard.ui;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.avatar.AvatarVariant;
import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.badge.BadgeVariant;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import dev.gatzka.flowboard.ui.i18n.FlowboardI18NProvider;
import dev.gatzka.flowboard.ui.i18n.LocalePreference;
import java.util.Locale;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.theme.lumo.Lumo;
import jakarta.annotation.security.PermitAll;
import dev.gatzka.flowboard.domain.BoardSummary;
import dev.gatzka.flowboard.domain.NotificationSnapshot;
import dev.gatzka.flowboard.ui.support.DesktopAlerts;
import dev.gatzka.flowboard.service.AccessService;
import dev.gatzka.flowboard.service.BoardChangeBroadcaster;
import dev.gatzka.flowboard.service.BoardService;
import dev.gatzka.flowboard.service.InboxBroadcaster;
import dev.gatzka.flowboard.service.NotificationService;
import dev.gatzka.flowboard.service.TimeService;
import dev.gatzka.flowboard.domain.TimerSnapshot;
import dev.gatzka.flowboard.ui.support.Durations;
import java.util.Optional;
import dev.gatzka.flowboard.service.VisitService;
import dev.gatzka.flowboard.service.WorkService;
import dev.gatzka.flowboard.ui.board.BoardView;
import dev.gatzka.flowboard.ui.boards.BoardsView;
import dev.gatzka.flowboard.ui.support.UiAccess;
import dev.gatzka.flowboard.security.ApiTokenService;
import dev.gatzka.flowboard.ui.account.ApiTokensDialog;
import dev.gatzka.flowboard.ui.team.TeamView;
import dev.gatzka.flowboard.ui.work.MyWorkView;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Application frame: navigation drawer with all boards, theme toggle and the current user. */
@Layout
@PermitAll
public class MainLayout extends AppLayout implements LocaleChangeObserver {

    private final BoardService boardService;
    private final BoardChangeBroadcaster broadcaster;
    private final UserSession session;
    private final SearchDialog searchDialog;
    private final NotificationService notificationService;
    private final VisitService visits;
    private final TimeService timeService;
    private final AccessService access;
    private final ApiTokenService apiTokens;
    private final Clock clock;
    private final InboxBroadcaster inboxBroadcaster;
    private final InboxDialog inboxDialog;
    private final Button inboxButton = new Button();
    private final Badge unread = new Badge();
    private Registration inboxSubscription;
    private Registration profileSubscription;

    private final SideNav boardsNav = new SideNav();
    private final SideNavItem homeItem = new SideNavItem("", BoardsView.class, VaadinIcon.HOME_O.create());
    private final SideNavItem myWorkItem = new SideNavItem("", MyWorkView.class, VaadinIcon.USER_CHECK.create());
    private final SideNavItem teamItem = new SideNavItem("", TeamView.class, VaadinIcon.GROUP.create());
    private final Button search = new Button(VaadinIcon.SEARCH.create());
    private final Button help = new Button(VaadinIcon.QUESTION_CIRCLE_O.create());
    private MenuItem changeNameItem;
    private MenuItem tokensItem;
    private MenuItem languageItem;
    private MenuItem signOutItem;
    private final Button themeToggle = new Button();
    private final Button userButton = new Button();
    private final Button timerChip = new Button();
    private Registration timerSubscription;
    private Registration changeSubscription;
    private Registration navigationSubscription;

    public MainLayout(BoardService boardService, WorkService workService, NotificationService notificationService,
            BoardChangeBroadcaster broadcaster, InboxBroadcaster inboxBroadcaster, VisitService visits, TimeService timeService,
            AccessService access, ApiTokenService apiTokens, UserSession session, Clock clock) {
        this.boardService = boardService;
        this.apiTokens = apiTokens;
        this.clock = clock;
        this.access = access;
        this.visits = visits;
        this.timeService = timeService;
        this.broadcaster = broadcaster;
        this.session = session;
        this.notificationService = notificationService;
        this.inboxBroadcaster = inboxBroadcaster;
        this.inboxDialog = new InboxDialog(notificationService, session, this::refreshInbox);
        this.searchDialog = new SearchDialog(workService, this::commands, location -> access.canAccess(location.boardId(), session.login()), clock);
        Shortcuts.addShortcutListener(this, event -> searchDialog.open(), Key.KEY_K, KeyModifier.CONTROL);
        Shortcuts.addShortcutListener(this, event -> new ShortcutsDialog().open(), Key.SLASH, KeyModifier.CONTROL);

        setPrimarySection(Section.DRAWER);
        addToNavbar(true, header());
        addToDrawer(drawer());
        relabel();

        applyTheme(session.isDarkMode());
        if (!session.hasThemePreference()) {
            followBrowserPreference();
        }
        showUser();
        refreshBoards();
    }

    /** On the first visit, adopt the operating system's light or dark preference. */
    private void followBrowserPreference() {
        UI.getCurrent().getPage()
                .executeJs("return window.matchMedia('(prefers-color-scheme: dark)').matches")
                .then(Boolean.class, prefersDark -> {
                    if (!session.hasThemePreference()) {
                        session.setDarkMode(prefersDark);
                        applyTheme(prefersDark);
                    }
                });
    }

    /** What the command palette offers: navigation, board shortcuts and a few toggles. */
    private List<Command> commands() {
        List<Command> list = new ArrayList<>();
        list.add(new Command(getTranslation("command.newBoard"), VaadinIcon.PLUS, () -> getUI().ifPresent(ui -> {
            ui.navigate(BoardsView.class);
            ComponentUtil.fireEvent(ui, new NewBoardRequestedEvent(ui));
        })));
        list.add(new Command(getTranslation("nav.home"), VaadinIcon.HOME_O, () -> getUI().ifPresent(ui -> ui.navigate(BoardsView.class))));
        list.add(new Command(getTranslation("nav.myWork"), VaadinIcon.USER_CHECK, () -> getUI().ifPresent(ui -> ui.navigate(MyWorkView.class))));
        list.add(new Command(getTranslation("nav.team"), VaadinIcon.GROUP, () -> getUI().ifPresent(ui -> ui.navigate(TeamView.class))));
        list.add(new Command(getTranslation(session.isDarkMode() ? "theme.toLight" : "theme.toDark"), VaadinIcon.ADJUST, () -> {
            session.setDarkMode(!session.isDarkMode());
            applyTheme(session.isDarkMode());
        }));
        list.add(new Command(getTranslation("shortcuts.title"), VaadinIcon.QUESTION_CIRCLE_O, () -> new ShortcutsDialog().open()));
        list.add(new Command(getTranslation("account.changeName"), VaadinIcon.USER, () -> new ProfileDialog(session, this::onProfileSaved).open()));
        list.add(new Command(getTranslation("tokens.title"), VaadinIcon.KEY, () -> new ApiTokensDialog(apiTokens, session.login(), clock).open()));
        list.add(new Command(getTranslation("account.signOut"), VaadinIcon.SIGN_OUT, session::signOut));
        for (BoardSummary board : access.visibleBoards(session.login())) {
            RouteParameters parameters = new RouteParameters(BoardView.BOARD_ID, Long.toString(board.id()));
            list.add(new Command(getTranslation("command.openBoard", board.name()), VaadinIcon.TASKS, () -> getUI().ifPresent(ui -> ui.navigate(BoardView.class, parameters))));
        }
        return list;
    }

    private Div header() {
        H1 title = new H1("Flowboard");
        title.addClassName("app-title");

        themeToggle.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        themeToggle.addClickListener(event -> {
            session.setDarkMode(!session.isDarkMode());
            applyTheme(session.isDarkMode());
        });

        userButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        userButton.addClassName("user-button");
        ContextMenu userMenu = new ContextMenu(userButton);
        userMenu.setOpenOnClick(true);
        changeNameItem = userMenu.addItem("", event -> new ProfileDialog(session, this::onProfileSaved).open());
        tokensItem = userMenu.addItem("", event -> new ApiTokensDialog(apiTokens, session.login(), clock).open());
        tokensItem.setId("api-tokens");
        languageItem = userMenu.addItem("");
        languageItem.setId("language");
        for (Locale locale : List.of(FlowboardI18NProvider.ENGLISH, FlowboardI18NProvider.GERMAN)) {
            MenuItem choice = languageItem.getSubMenu().addItem(locale.getDisplayLanguage(locale),
                    event -> getUI().ifPresent(ui -> LocalePreference.choose(ui, locale)));
            choice.setId("lang-" + locale.getLanguage());
            choice.setCheckable(true);
            choice.setChecked(FlowboardI18NProvider.supported(UI.getCurrent().getLocale()).equals(locale));
        }
        signOutItem = userMenu.addItem("", event -> session.signOut());
        signOutItem.setId("sign-out");

        search.addClickListener(event -> searchDialog.open());
        search.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);

        inboxButton.setIcon(VaadinIcon.BELL_O.create());
        inboxButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        inboxButton.addClassName("inbox-button");
        inboxButton.setSuffixComponent(unread);
        unread.addThemeVariants(BadgeVariant.SMALL, BadgeVariant.ERROR, BadgeVariant.FILLED);
        unread.addClassName("inbox-button__count");
        inboxButton.addClickListener(event -> {
            inboxDialog.refresh();
            inboxDialog.open();
        });

        help.addClickListener(event -> new ShortcutsDialog().open());
        help.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);

        timerChip.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_CONTRAST);
        timerChip.setIcon(VaadinIcon.TIMER.create());
        timerChip.addClassName("timer-chip");
        timerChip.setVisible(false);
        timerChip.addClickListener(event -> openTimedCard());

        Div header = new Div(new DrawerToggle(), title, new Span(), timerChip, search, inboxButton, help, themeToggle, userButton);
        header.addClassName("app-header");
        return header;
    }

    private Scroller drawer() {
        SideNav mainNav = new SideNav();
        mainNav.addItem(homeItem, myWorkItem, teamItem);

        Div content = new Div(mainNav, boardsNav);
        content.addClassName("drawer-content");
        return new Scroller(content);
    }

    /** Every fixed text of the shell in the current language; runs once and again whenever the language changes. */
    private void relabel() {
        boardsNav.setLabel(getTranslation("nav.boards"));
        homeItem.setLabel(getTranslation("nav.home"));
        myWorkItem.setLabel(getTranslation("nav.myWork"));
        teamItem.setLabel(getTranslation("nav.team"));
        changeNameItem.setText(getTranslation("account.changeName"));
        tokensItem.setText(getTranslation("tokens.menu"));
        languageItem.setText(getTranslation("language.menu"));
        signOutItem.setText(getTranslation("account.signOut"));
        search.setAriaLabel(getTranslation("search.aria"));
        search.setTooltipText(getTranslation("search.tooltip"));
        help.setAriaLabel(getTranslation("shortcuts.title"));
        help.setTooltipText(getTranslation("shortcuts.tooltip"));
        userButton.setTooltipText(getTranslation("account.title"));
        applyTheme(session.isDarkMode());
        refreshInbox();
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        relabel();
        refreshBoards();
        showTimer();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        UI ui = attachEvent.getUI();
        changeSubscription = broadcaster.subscribe(event -> UiAccess.run(ui, this::refreshBoards));
        navigationSubscription = ui.addAfterNavigationListener(event -> {
            refreshBoards();
            showTimer();
        });
        timerSubscription = ComponentUtil.addListener(ui, TimerChangedEvent.class, event -> showTimer());
        showTimer();
        subscribeInbox(ui);
        profileSubscription = ComponentUtil.addListener(ui, ProfileChangedEvent.class, event -> {
            unsubscribeInbox();
            subscribeInbox(ui);
        });
        refreshInbox();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (changeSubscription != null) {
            changeSubscription.remove();
        navigationSubscription.remove();
        timerSubscription.remove();
            changeSubscription = null;
        }
        if (profileSubscription != null) {
            profileSubscription.remove();
            profileSubscription = null;
        }
        unsubscribeInbox();
    }

    private void subscribeInbox(UI ui) {
        inboxSubscription = inboxBroadcaster.subscribe(session.getDisplayName(), () -> UiAccess.run(ui, () -> {
            refreshInbox();
            showTimer(); // a finished focus session announces itself through the inbox
            if (inboxDialog.isOpened()) {
                inboxDialog.refresh();
            }
            notificationService.recent(session.getDisplayName()).stream().filter(NotificationSnapshot::unread).findFirst()
                    .ifPresent(latest -> DesktopAlerts.show(ui, latest.actor(), latest.message()));
        }));
    }

    private void unsubscribeInbox() {
        if (inboxSubscription != null) {
            inboxSubscription.remove();
            inboxSubscription = null;
        }
    }

    /** Updates the unread counter on the bell. */
    void refreshInbox() {
        long count = notificationService.unreadCount(session.getDisplayName());
        unread.setText(Long.toString(count));
        unread.setVisible(count > 0);
        inboxButton.setAriaLabel(count == 0 ? getTranslation("inbox.title") : getTranslation("inbox.unreadAria", count));
    }

    private void refreshBoards() {
        boardsNav.removeAll();
        Map<Long, Long> unread = visits.unreadCounts(session.login(), session.getDisplayName());
        for (BoardSummary board : access.visibleBoards(session.login())) {
            RouteParameters parameters = new RouteParameters(BoardView.BOARD_ID, Long.toString(board.id()));
            SideNavItem item = new SideNavItem(board.name(), BoardView.class, parameters, VaadinIcon.TASKS.create());
            Long count = unread.get(board.id());
            if (count != null) {
                Badge badge = new Badge(count > 99 ? "99+" : Long.toString(count));
                badge.addThemeVariants(BadgeVariant.SMALL, BadgeVariant.FILLED);
                badge.addClassName("nav-unread");
                badge.getElement().setAttribute("title", getTranslation("nav.unreadTitle", count));
                item.setSuffixComponent(badge);
            }
            boardsNav.addItem(item);
        }
    }

    private void applyTheme(boolean dark) {
        getUI().orElseGet(UI::getCurrent).getElement().getThemeList().set(Lumo.DARK, dark);
        Icon icon = (dark ? VaadinIcon.SUN_O : VaadinIcon.MOON_O).create();
        themeToggle.setIcon(icon);
        themeToggle.setAriaLabel(getTranslation(dark ? "theme.toLight" : "theme.toDark"));
        themeToggle.setTooltipText(getTranslation(dark ? "theme.light" : "theme.dark"));
    }

    /** A chip in the header while the user has a timer running; clicking it opens the card. */
    private void showTimer() {
        Optional<TimerSnapshot> running = timeService.running(session.getDisplayName());
        timerChip.setVisible(running.isPresent());
        timerChip.removeClassName("timer-chip--focus");
        running.ifPresent(timer -> {
            timerChip.getElement().setAttribute("data-card-id", Long.toString(timer.cardId()));
            if (timer.isFocusSession()) {
                timerChip.addClassName("timer-chip--focus");
                timerChip.setText(timer.cardTitle());
                long today = timeService.focusSessionsToday(session.getDisplayName());
                timerChip.setTooltipText(getTranslation("focus.chipTooltip", today));
                timerChip.getElement().executeJs("""
                        const chip = this; const endsAt = $0;
                        if (chip._countdown) clearInterval(chip._countdown);
                        const label = chip.querySelector('.timer-chip__countdown') || document.createElement('span');
                        label.className = 'timer-chip__countdown';
                        chip.prepend(label);
                        const tick = () => {
                          const left = Math.max(0, Math.round((endsAt - Date.now()) / 1000));
                          label.textContent = Math.floor(left / 60) + ':' + String(left % 60).padStart(2, '0') + ' · ';
                          if (left === 0) clearInterval(chip._countdown);
                        };
                        tick(); chip._countdown = setInterval(tick, 1000);
                        """, timer.endsAt().toEpochMilli());
            } else {
                timerChip.setText(timer.cardTitle());
                timerChip.setTooltipText(getTranslation("timer.chipTooltip", Durations.since(timer.startedAt(), java.time.Instant.now())));
                timerChip.getElement().executeJs("if (this._countdown) clearInterval(this._countdown);");
            }
        });
    }

    private void openTimedCard() {
        timeService.running(session.getDisplayName()).ifPresent(timer -> getUI().ifPresent(ui -> ui.navigate(BoardView.class,
                new RouteParameters(java.util.Map.of(BoardView.BOARD_ID, Long.toString(timer.boardId()), BoardView.CARD_ID, Long.toString(timer.cardId()))))));
    }

    private void showUser() {
        Avatar avatar = new Avatar(session.getDisplayName());
        avatar.setColorIndex(session.participant().colorIndex());
        avatar.addThemeVariants(AvatarVariant.LUMO_XSMALL);
        userButton.setIcon(avatar);
        userButton.setText(session.getDisplayName());
    }

    private void onProfileSaved() {
        showUser();
        getUI().ifPresent(ui -> ComponentUtil.fireEvent(ui, new ProfileChangedEvent(ui)));
    }
}
