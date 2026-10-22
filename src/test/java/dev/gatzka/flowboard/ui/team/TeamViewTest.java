package dev.gatzka.flowboard.ui.team;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.IntegerField;
import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.Priority;
import dev.gatzka.flowboard.service.BoardService;
import dev.gatzka.flowboard.ui.FlowboardUiTest;
import dev.gatzka.flowboard.ui.work.CardLocationRow;
import java.time.LocalDate;
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
class TeamViewTest extends FlowboardUiTest {

    @Autowired
    private BoardService service;

    private BoardSnapshot board;

    @BeforeEach
    void seed() {
        service.listBoards().forEach(existing -> service.deleteBoard(existing.id(), "Seeder"));
        board = service.createBoard(new BoardDraft("Team", null), "Seeder");
    }

    @Test
    void showsLoadPerPersonAndWeekAndReassignsOnRequest() {
        long backlog = board.lanes().get(0).id();
        LocalDate today = LocalDate.now();
        card(backlog, "Big one", "Priya", today, 8);
        card(backlog, "Second", "Priya", today, 5);
        card(backlog, "Small", "Jonas", today, 2);
        card(backlog, "Nobody's", null, today, 1);
        service.createCard(board.lanes().get(3).id(), new CardDraft("Done already", null, Priority.LOW, today, "Priya", Set.of(), 13), "Seeder");

        navigate(TeamView.class);

        assertThat($(Span.class).withClassName("page-subtitle").single().getText()).startsWith("4 open cards across 3 people");
        assertThat($(IntegerField.class).withId("team-capacity").single().getValue()).isEqualTo(TeamView.DEFAULT_POINTS_CAPACITY);
        Div priya = $(Div.class).withClassName("team__row").withCondition(row -> "Priya".equals(row.getElement().getAttribute("data-person"))).single();
        assertThat($(Span.class, priya).withClassName("team__total").single().getText()).isEqualTo("13 pt");
        assertThat($(Div.class, priya).withClassName("team__cell--over").all()).hasSize(1);
        Div jonas = $(Div.class).withClassName("team__row").withCondition(row -> "Jonas".equals(row.getElement().getAttribute("data-person"))).single();
        assertThat($(Div.class, jonas).withClassName("team__cell--over").all()).isEmpty();
        assertThat($(Div.class).withClassName("team__row").withCondition(row -> "Unassigned".equals(row.getElement().getAttribute("data-person"))).all()).hasSize(1);

        // the cell dialog lists the cards behind a number
        test($(Button.class, priya).withClassName("team__open").first()).click();
        Dialog cards = $(Dialog.class).withClassName("team__cards").single();
        assertThat($(CardLocationRow.class, cards).all()).hasSize(2);
        cards.close();

        // suggestions: Priya (13) hands the largest card that fits to Jonas (2, room 8) -> "Big one" (8); the unassigned card then goes to Priya
        assertThat($(Div.class).withClassName("team__suggestion").all()).hasSizeGreaterThanOrEqualTo(2);
        Div first = $(Div.class).withClassName("team__suggestion").first();
        assertThat($(Span.class, first).withClassName("team__suggestion-card").single().getText()).isEqualTo("Big one");
        assertThat(first.getElement().getTextRecursively()).contains("from Priya to Jonas");

        test($(Button.class, first).withClassName("team__reassign").single()).click();
        pump();

        assertThat(service.findBoard(board.id()).orElseThrow().lanes().get(0).cards())
                .filteredOn(card -> card.title().equals("Big one")).singleElement().extracting(CardSnapshot::assignee).isEqualTo("Jonas");
        Div priyaAfter = $(Div.class).withClassName("team__row").withCondition(row -> "Priya".equals(row.getElement().getAttribute("data-person"))).single();
        assertThat($(Span.class, priyaAfter).withClassName("team__total").single().getText()).isEqualTo("5 pt");

        // raising the capacity clears the overload marker
        test($(IntegerField.class).withId("team-capacity").single()).setValue(50);
        assertThat($(Div.class).withClassName("team__cell--over").all()).isEmpty();
    }

    @Test
    void emptyBoardsSayNothingIsOpen() {
        navigate(TeamView.class);

        assertThat($(Span.class).withClassName("page-subtitle").single().getText()).isEqualTo("No open cards on the boards you can see.");
        assertThat($(Div.class).withClassName("team__row").all()).isEmpty();
    }

    private void card(long laneId, String title, String assignee, LocalDate due, Integer estimate) {
        service.createCard(laneId, new CardDraft(title, null, Priority.MEDIUM, due, assignee, Set.of(), estimate), "Seeder");
    }
}
