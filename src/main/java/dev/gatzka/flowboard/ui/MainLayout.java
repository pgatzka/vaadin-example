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
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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
import dev.gatzka.flowboard.domain.BoardSummary;
import dev.gatzka.flowboard.service.BoardChangeBroadcaster;
import dev.gatzka.flowboard.service.BoardService;
import dev.gatzka.flowboard.service.WorkService;
import dev.gatzka.flowboard.ui.board.BoardView;
import dev.gatzka.flowboard.ui.boards.BoardsView;
import dev.gatzka.flowboard.ui.support.UiAccess;
import dev.gatzka.flowboard.ui.work.MyWorkView;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/** Application frame: navigation drawer with all boards, theme toggle and the current user. */
@Layout
public class MainLayout extends AppLayout {

    private final BoardService boardService;
    private final BoardChangeBroadcaster broadcaster;
    private final UserSession session;
    private final SearchDialog searchDialog;

    private final SideNav boardsNav = new SideNav("Boards");
    private final Button themeToggle = new Button();
    private final Button userButton = new Button();
    private Registration changeSubscription;

    public MainLayout(BoardService boardService, WorkService workService, BoardChangeBroadcaster broadcaster,
            UserSession session, Clock clock) {
        this.boardService = boardService;
        this.broadcaster = broadcaster;
        this.session = session;
        this.searchDialog = new SearchDialog(workService, this::commands, clock);
        Shortcuts.addShortcutListener(this, event -> searchDialog.open(), Key.KEY_K, KeyModifier.CONTROL);
        Shortcuts.addShortcutListener(this, event -> new ShortcutsDialog().open(), Key.SLASH, KeyModifier.CONTROL);

        setPrimarySection(Section.DRAWER);
        addToNavbar(true, header());
        addToDrawer(drawer());

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
        list.add(new Command("New board", VaadinIcon.PLUS, () -> getUI().ifPresent(ui -> {
            ui.navigate(BoardsView.class);
            ComponentUtil.fireEvent(ui, new NewBoardRequestedEvent(ui));
        })));
        list.add(new Command("All boards", VaadinIcon.GRID_BIG_O, () -> getUI().ifPresent(ui -> ui.navigate(BoardsView.class))));
        list.add(new Command("My work", VaadinIcon.USER_CHECK, () -> getUI().ifPresent(ui -> ui.navigate(MyWorkView.class))));
        list.add(new Command(session.isDarkMode() ? "Switch to light theme" : "Switch to dark theme", VaadinIcon.ADJUST, () -> {
            session.setDarkMode(!session.isDarkMode());
            applyTheme(session.isDarkMode());
        }));
        list.add(new Command("Keyboard shortcuts", VaadinIcon.QUESTION_CIRCLE_O, () -> new ShortcutsDialog().open()));
        list.add(new Command("Change display name", VaadinIcon.USER, () -> new ProfileDialog(session, this::onProfileSaved).open()));
        for (BoardSummary board : boardService.listBoards()) {
            RouteParameters parameters = new RouteParameters(BoardView.BOARD_ID, Long.toString(board.id()));
            list.add(new Command("Open board: " + board.name(), VaadinIcon.TASKS, () -> getUI().ifPresent(ui -> ui.navigate(BoardView.class, parameters))));
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
        userButton.addClickListener(event -> new ProfileDialog(session, this::onProfileSaved).open());

        Button search = new Button(VaadinIcon.SEARCH.create(), event -> searchDialog.open());
        search.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        search.setAriaLabel("Search all boards");
        search.setTooltipText("Search (Ctrl+K)");

        Button help = new Button(VaadinIcon.QUESTION_CIRCLE_O.create(), event -> new ShortcutsDialog().open());
        help.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        help.setAriaLabel("Keyboard shortcuts");
        help.setTooltipText("Keyboard shortcuts (Ctrl+/)");

        Div header = new Div(new DrawerToggle(), title, new Span(), search, help, themeToggle, userButton);
        header.addClassName("app-header");
        return header;
    }

    private Scroller drawer() {
        SideNav mainNav = new SideNav();
        mainNav.addItem(new SideNavItem("All boards", BoardsView.class, VaadinIcon.GRID_BIG_O.create()));
        mainNav.addItem(new SideNavItem("My work", MyWorkView.class, VaadinIcon.USER_CHECK.create()));

        Div content = new Div(mainNav, boardsNav);
        content.addClassName("drawer-content");
        return new Scroller(content);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        UI ui = attachEvent.getUI();
        changeSubscription = broadcaster.subscribe(event -> {
            if (event.affectsBoardList()) {
                UiAccess.run(ui, this::refreshBoards);
            }
        });
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (changeSubscription != null) {
            changeSubscription.remove();
            changeSubscription = null;
        }
    }

    private void refreshBoards() {
        boardsNav.removeAll();
        for (BoardSummary board : boardService.listBoards()) {
            RouteParameters parameters = new RouteParameters(BoardView.BOARD_ID, Long.toString(board.id()));
            boardsNav.addItem(new SideNavItem(board.name(), BoardView.class, parameters, VaadinIcon.TASKS.create()));
        }
    }

    private void applyTheme(boolean dark) {
        getUI().orElseGet(UI::getCurrent).getElement().getThemeList().set(Lumo.DARK, dark);
        Icon icon = (dark ? VaadinIcon.SUN_O : VaadinIcon.MOON_O).create();
        themeToggle.setIcon(icon);
        themeToggle.setAriaLabel(dark ? "Switch to light theme" : "Switch to dark theme");
        themeToggle.setTooltipText(dark ? "Light theme" : "Dark theme");
    }

    private void showUser() {
        Avatar avatar = new Avatar(session.getDisplayName());
        avatar.setColorIndex(session.participant().colorIndex());
        avatar.addThemeVariants(AvatarVariant.LUMO_XSMALL);
        userButton.setIcon(avatar);
        userButton.setText(session.getDisplayName());
        userButton.setTooltipText("Change your display name");
    }

    private void onProfileSaved() {
        showUser();
        getUI().ifPresent(ui -> ComponentUtil.fireEvent(ui, new ProfileChangedEvent(ui)));
    }
}
