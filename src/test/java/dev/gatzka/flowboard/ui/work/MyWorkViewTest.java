package dev.gatzka.flowboard.ui.work;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;
import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.Priority;
import dev.gatzka.flowboard.service.BoardService;
import dev.gatzka.flowboard.ui.UserSession;
import dev.gatzka.flowboard.ui.board.BoardView;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "mara")
class MyWorkViewTest extends SpringBrowserlessTest {

    @Autowired
    private BoardService service;

    @Autowired
    private ApplicationContext context;

    private BoardSnapshot board;

    @BeforeEach
    void seed() {
        service.listBoards().forEach(existing -> service.deleteBoard(existing.id(), "Seeder"));
        board = service.createBoard(new BoardDraft("Work", null), "Seeder");
    }

    @Test
    void groupsAssignedCardsByUrgency() {
        UserSession session = context.getBean(UserSession.class);
        session.setDisplayName("Priya");
        long backlog = board.lanes().get(0).id();
        service.createCard(backlog, new CardDraft("Late", null, Priority.HIGH, LocalDate.now().minusDays(1), "Priya", Set.of(), null), "Seeder");
        service.createCard(backlog, new CardDraft("Today", null, Priority.LOW, LocalDate.now(), "priya", Set.of(), null), "Seeder");
        service.createCard(backlog, new CardDraft("Someday", null, Priority.LOW, null, "Priya", Set.of(), null), "Seeder");
        service.createCard(backlog, new CardDraft("Not mine", null, Priority.LOW, null, "Mara", Set.of(), null), "Seeder");

        navigate(MyWorkView.class);

        assertThat($(Div.class).withClassName("work-group").all())
                .extracting(group -> group.getElement().getAttribute("data-bucket"))
                .containsExactly("overdue", "today", "undated");
        assertThat($(CardLocationRow.class).all()).extracting(row -> $(Span.class, row).withClassName("card-row__title").single().getText())
                .containsExactly("Late", "Today", "Someday");
        assertThat($(Span.class).withClassName("page-subtitle").single().getText()).startsWith("3 open cards assigned to Priya");
    }

    @Test
    void commandPaletteOffersActionsAndBoards() {
        navigate(MyWorkView.class);
        test($(Button.class).withCondition(b -> "Search all boards".equals(b.getAriaLabel().orElse(null))).single()).click();

        assertThat($(Button.class).withClassName("palette__command").all()).extracting(Button::getText)
                .contains("New board", "My work", "Switch to dark theme", "Open board: Work");

        test($(TextField.class).withPlaceholder("Search boards and cards, or type a command").single()).setValue("dark");
        test($(Button.class).withClassName("palette__command").withText("Switch to dark theme").single()).click();

        assertThat(context.getBean(UserSession.class).isDarkMode()).isTrue();
    }

    @Test
    void searchDialogFindsCardsOnAnyBoard() {
        service.createCard(board.lanes().get(1).id(), CardDraft.titled("Needle in haystack"), "Seeder");
        navigate(MyWorkView.class);

        test($(Button.class).withCondition(b -> "Search all boards".equals(b.getAriaLabel().orElse(null))).single()).click();
        test($(TextField.class).withPlaceholder("Search boards and cards, or type a command").single()).setValue("needle");

        assertThat($(CardLocationRow.class).all()).hasSize(1);
        test($(CardLocationRow.class).single()).click();
        assertThat(getCurrentView()).isInstanceOf(BoardView.class);
    }
}
