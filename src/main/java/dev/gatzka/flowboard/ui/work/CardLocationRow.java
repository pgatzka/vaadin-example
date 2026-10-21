package dev.gatzka.flowboard.ui.work;

import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.badge.BadgeVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RouterLink;
import dev.gatzka.flowboard.domain.CardLocation;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.ui.board.BoardView;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/** One card in a cross-board list; a link that opens the card on its board. */
public final class CardLocationRow extends RouterLink {

    private static final DateTimeFormatter DUE = DateTimeFormatter.ofPattern("MMM d");

    public CardLocationRow(CardLocation location, LocalDate today) {
        super(BoardView.class, new RouteParameters(Map.of(
                BoardView.BOARD_ID, Long.toString(location.boardId()),
                BoardView.CARD_ID, Long.toString(location.card().id()))));
        CardSnapshot card = location.card();
        addClassName("card-row");
        getElement().setAttribute("data-priority", card.priority().name().toLowerCase(Locale.ROOT));

        Span title = new Span(card.title());
        title.addClassName("card-row__title");
        Span where = new Span(location.boardName() + " · " + location.laneName());
        where.addClassName("card-row__where");
        add(title, where);

        if (card.dueDate() != null) {
            Badge due = new Badge(DUE.format(card.dueDate()), VaadinIcon.CALENDAR_O.create());
            due.addThemeVariants(BadgeVariant.SMALL);
            if (location.isCompleted()) {
                due.addThemeVariants(BadgeVariant.SUCCESS);
            } else if (card.isOverdue(today)) {
                due.addThemeVariants(BadgeVariant.ERROR);
            } else if (card.isDueSoon(today)) {
                due.addThemeVariants(BadgeVariant.WARNING);
            }
            add(due);
        }
    }
}
