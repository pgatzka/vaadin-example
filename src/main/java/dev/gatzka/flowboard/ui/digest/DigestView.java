package dev.gatzka.flowboard.ui.digest;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.markdown.Markdown;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import dev.gatzka.flowboard.domain.WeeklyDigest;
import dev.gatzka.flowboard.service.DigestService;
import dev.gatzka.flowboard.service.NotFoundException;
import dev.gatzka.flowboard.ui.board.BoardView;
import dev.gatzka.flowboard.ui.boards.BoardsView;
import dev.gatzka.flowboard.ui.support.Toasts;
import jakarta.annotation.security.PermitAll;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/** A printable weekly report of a board, also available as Markdown to paste into a chat or mail. */
@Route("board/:boardId/digest/:week?")
@PermitAll
public class DigestView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

    static final String WEEK = "week";

    private final DigestService digests;
    private final Clock clock;
    private final H2 heading = new H2();
    private final Span period = new Span();
    private final Markdown markdown = new Markdown();
    private long boardId;
    private WeeklyDigest digest;

    public DigestView(DigestService digests, Clock clock) {
        this.digests = digests;
        this.clock = clock;
        addClassName("digest-view");
        setSizeFull();
        setPadding(false);
        setSpacing(false);

        heading.addClassName("board-toolbar__title");
        period.addClassName("digest__period");
        Div titles = new Div(heading, period);
        titles.addClassName("board-toolbar__titles");

        Button previous = new Button(VaadinIcon.ANGLE_LEFT.create(), event -> show(digest.to().minusWeeks(1)));
        previous.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        previous.setAriaLabel("Previous week");
        Button next = new Button(VaadinIcon.ANGLE_RIGHT.create(), event -> show(digest.to().plusWeeks(1)));
        next.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        next.setAriaLabel("Next week");
        Button thisWeek = new Button("This week", event -> show(LocalDate.now(clock)));
        thisWeek.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Button copy = new Button("Copy as Markdown", VaadinIcon.COPY_O.create(), event -> copy());
        copy.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        copy.setId("copy-digest");
        Button print = new Button("Print", VaadinIcon.PRINT.create(), event -> getUI().ifPresent(ui -> ui.getPage().executeJs("window.print()")));
        print.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Button backToBoard = new Button("Board", VaadinIcon.TASKS.create(), event -> getUI().ifPresent(
                ui -> ui.navigate(BoardView.class, new RouteParameters(BoardView.BOARD_ID, Long.toString(boardId)))));
        backToBoard.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Div actions = new Div(previous, next, thisWeek, copy, print, backToBoard);
        actions.addClassName("digest__actions");

        Div toolbar = new Div(titles, actions);
        toolbar.addClassName("board-toolbar");

        markdown.addClassName("markdown-preview");
        markdown.addClassName("digest__body");
        Div sheet = new Div(markdown);
        sheet.addClassName("digest__sheet");
        add(toolbar, sheet);
        setFlexGrow(1, sheet);
    }

    public static String pathTo(long boardId) {
        return "board/" + boardId + "/digest";
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        boardId = event.getRouteParameters().getLong(BoardView.BOARD_ID).orElse(-1L);
        LocalDate weekEnding = event.getRouteParameters().get(WEEK).flatMap(DigestView::parse).orElse(LocalDate.now(clock));
        try {
            show(weekEnding);
        } catch (NotFoundException gone) {
            Toasts.error("That board does not exist");
            event.forwardTo(BoardsView.class);
        }
    }

    @Override
    public String getPageTitle() {
        return (digest == null ? "Digest" : "Digest · " + digest.boardName()) + " · Flowboard";
    }

    private void show(LocalDate weekEnding) {
        digest = digests.weekly(boardId, weekEnding);
        heading.setText("Weekly digest · " + digest.boardName());
        period.setText(digest.period());
        markdown.setContent(digest.toMarkdown());
        getUI().ifPresent(ui -> ui.getPage().getHistory().replaceState(null,
                pathTo(boardId) + "/" + weekEnding));
    }

    private void copy() {
        String text = digest.toMarkdown();
        getUI().ifPresent(ui -> ui.getPage().executeJs("navigator.clipboard.writeText($0)", text));
        Toasts.success("Digest copied as Markdown");
    }

    WeeklyDigest digest() {
        return digest;
    }

    private static Optional<LocalDate> parse(String value) {
        try {
            return Optional.of(LocalDate.parse(value));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
