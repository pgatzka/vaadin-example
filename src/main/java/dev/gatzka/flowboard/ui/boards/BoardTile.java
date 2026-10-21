package dev.gatzka.flowboard.ui.boards;

import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.badge.BadgeVariant;
import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.card.CardVariant;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RouterLink;
import dev.gatzka.flowboard.domain.BoardSummary;
import dev.gatzka.flowboard.ui.board.BoardView;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

/** One board in the overview grid. */
final class BoardTile extends Card {

    private static final DateTimeFormatter CREATED = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

    BoardTile(BoardSummary board, Runnable onOpen, Runnable onEdit, Runnable onExport, Runnable onDelete) {
        addClassName("board-tile");
        addThemeVariants(CardVariant.LUMO_OUTLINED, CardVariant.LUMO_ELEVATED);

        RouteParameters parameters = new RouteParameters(BoardView.BOARD_ID, Long.toString(board.id()));
        RouterLink title = new RouterLink(board.name(), BoardView.class, parameters);
        title.getElement().addEventListener("click", event -> { }).stopPropagation();
        setTitle(title);
        setSubtitle(new Span("Created " + CREATED.format(board.createdAt().atZone(ZoneId.systemDefault()))));
        setHeaderSuffix(menu(onEdit, onExport, onDelete));

        Paragraph description = new Paragraph(board.description() == null ? "No description yet." : board.description());
        description.addClassName("board-tile__description");
        add(description);

        addToFooter(count(board.laneCount(), "lane"), count(board.cardCount(), "card"));
        if (board.archivedCount() > 0) {
            Badge archived = new Badge(board.archivedCount() + " archived");
            archived.addThemeVariants(BadgeVariant.CONTRAST);
            addToFooter(archived);
        }
        if (board.overdueCount() > 0) {
            Badge overdue = new Badge(board.overdueCount() + " overdue");
            overdue.addThemeVariants(BadgeVariant.ERROR);
            addToFooter(overdue);
        }

        getElement().addEventListener("click", event -> onOpen.run());
    }

    private static Badge count(long count, String noun) {
        return new Badge(count + " " + (count == 1 ? noun : noun + "s"));
    }

    private static MenuBar menu(Runnable onEdit, Runnable onExport, Runnable onDelete) {
        MenuBar menu = new MenuBar();
        menu.addThemeVariants(MenuBarVariant.LUMO_TERTIARY_INLINE, MenuBarVariant.LUMO_ICON);
        menu.getElement().addEventListener("click", event -> { }).stopPropagation();
        MenuItem root = menu.addItem(VaadinIcon.ELLIPSIS_DOTS_V.create());
        root.setAriaLabel("Board actions");
        SubMenu actions = root.getSubMenu();
        actions.addItem("Edit details", event -> onEdit.run());
        actions.addItem("Export as JSON", event -> onExport.run());
        MenuItem delete = actions.addItem("Delete board", event -> onDelete.run());
        delete.addClassName("danger");
        return menu;
    }
}
