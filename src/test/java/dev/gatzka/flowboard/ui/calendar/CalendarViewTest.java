package dev.gatzka.flowboard.ui.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.RouterLink;
import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.Priority;
import dev.gatzka.flowboard.service.BoardService;
import dev.gatzka.flowboard.ui.board.BoardView;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "mara")
class CalendarViewTest extends SpringBrowserlessTest {

    @Autowired
    private BoardService service;

    private BoardSnapshot board;

    @BeforeEach
    void seed() {
        service.listBoards().forEach(existing -> service.deleteBoard(existing.id(), "Seeder"));
        board = service.createBoard(new BoardDraft("Dates", null), "Seeder");
        long backlog = board.lanes().get(0).id();
        service.createCard(backlog, new CardDraft("Due today", null, Priority.HIGH, LocalDate.now(), null, Set.of(), null), "Seeder");
        service.createCard(backlog, new CardDraft("Was due", null, Priority.LOW, LocalDate.now().minusDays(1), null, Set.of(), null), "Seeder");
        service.createCard(backlog, new CardDraft("Next month", null, Priority.LOW, LocalDate.now().plusMonths(1).withDayOfMonth(10), null, Set.of(), null), "Seeder");
        service.createCard(backlog, CardDraft.titled("Undated"), "Seeder");
    }

    @Test
    void showsCardsOnTheirDueDatesAndNavigatesMonths() {
        navigate(CalendarView.class, Map.of(BoardView.BOARD_ID, Long.toString(board.id())));

        assertThat($(Span.class).withClassName("calendar__month").single().getText())
                .isEqualTo(DateTimeFormatter.ofPattern("MMMM uuuu").format(YearMonth.now()));
        assertThat($(RouterLink.class).withClassName("calendar__card").all()).extracting(RouterLink::getText)
                .contains("Due today");
        assertThat($(RouterLink.class).withClassName("calendar__card--overdue").all()).extracting(RouterLink::getText)
                .containsExactly("Was due");
        assertThat($(Span.class).withClassName("calendar__undated").single().getText()).isEqualTo("1 card has no due date.");

        test($(Button.class).withCondition(b -> "Next month".equals(b.getAriaLabel().orElse(null))).single()).click();

        assertThat($(RouterLink.class).withClassName("calendar__card").all()).extracting(RouterLink::getText)
                .contains("Next month");
    }

    @Test
    void monthCanBeGivenInTheUrl() {
        navigate(CalendarView.class, Map.of(BoardView.BOARD_ID, Long.toString(board.id()), CalendarView.MONTH, "2030-03"));

        assertThat($(Span.class).withClassName("calendar__month").single().getText()).isEqualTo("March 2030");
    }
}
