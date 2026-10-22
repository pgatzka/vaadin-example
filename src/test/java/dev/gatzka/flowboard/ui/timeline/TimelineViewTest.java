package dev.gatzka.flowboard.ui.timeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.select.Select;
import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.Priority;
import dev.gatzka.flowboard.domain.Timeline;
import dev.gatzka.flowboard.service.BoardService;
import dev.gatzka.flowboard.ui.FlowboardUiTest;
import dev.gatzka.flowboard.ui.board.BoardView;
import dev.gatzka.flowboard.ui.boards.BoardsView;
import java.time.LocalDate;
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
class TimelineViewTest extends FlowboardUiTest {

    @Autowired
    private BoardService service;

    private BoardSnapshot board;
    private CardSnapshot spec;
    private CardSnapshot launch;

    @BeforeEach
    void seed() {
        service.listBoards().forEach(existing -> service.deleteBoard(existing.id(), "Seeder"));
        board = service.createBoard(new BoardDraft("Plan", null), "Seeder");
        long backlog = board.lanes().get(0).id();
        LocalDate today = LocalDate.now();
        spec = service.createCard(backlog, new CardDraft("Spec", null, Priority.HIGH, today, today.plusDays(4), "Mara", Set.of(), null), "Seeder");
        launch = service.createCard(backlog, new CardDraft("Launch", null, Priority.MEDIUM, null, today.plusDays(14), "Jonas", Set.of(), null), "Seeder");
        service.createCard(backlog, CardDraft.titled("Undated"), "Seeder");
    }

    @Test
    void drawsBarsAndReschedulesByDrag() {
        TimelineView view = navigate(TimelineView.class, Map.of(BoardView.BOARD_ID, Long.toString(board.id())));

        assertThat($(Div.class).withClassName("timeline__bar").all()).hasSize(2);
        assertThat($(Div.class).withClassName("timeline__bar--milestone").all()).hasSize(1);
        assertThat($(Div.class).withClassName("timeline__group").all()).extracting(g -> g.getElement().getAttribute("data-group")).containsExactly("Backlog");
        assertThat($(Span.class).withClassName("timeline__footer").single().getText()).startsWith("1 card has no dates");
        assertThat($(Div.class).withClassName("timeline__today").all()).hasSize(1);

        test($(Select.class).withId("timeline-group").single(), Timeline.GroupBy.class).selectItem("By person");
        assertThat($(Div.class).withClassName("timeline__group").all()).extracting(g -> g.getElement().getAttribute("data-group")).containsExactly("Jonas", "Mara");

        view.reschedule(Long.toString(spec.id()), "move", 3);
        CardSnapshot moved = card(spec.id());
        assertThat(moved.startDate()).isEqualTo(spec.startDate().plusDays(3));
        assertThat(moved.dueDate()).isEqualTo(spec.dueDate().plusDays(3));

        view.reschedule(Long.toString(spec.id()), "end", -10);
        CardSnapshot squeezed = card(spec.id());
        assertThat(squeezed.dueDate()).as("the end cannot pass the start").isEqualTo(squeezed.startDate());

        view.reschedule(Long.toString(launch.id()), "start", -5);
        CardSnapshot withStart = card(launch.id());
        assertThat(withStart.startDate()).as("pulling a milestone's start gives it a start date").isEqualTo(launch.dueDate().minusDays(5));
        assertThat($(Div.class).withClassName("timeline__bar--milestone").all()).isEmpty();

        // the window moves in steps of four weeks and comes back with Today
        String before = $(Span.class).withClassName("calendar__month").single().getText();
        test($(Button.class).withId("timeline-next").single()).click();
        assertThat($(Span.class).withClassName("calendar__month").single().getText()).isNotEqualTo(before);
        test($(Button.class).withId("timeline-today").single()).click();
        assertThat($(Span.class).withClassName("calendar__month").single().getText()).isEqualTo(before);
        test($(Button.class).withId("timeline-prev").single()).click();
        assertThat(view.timeline().from()).isEqualTo(Timeline.windowStart(LocalDate.now()).minusWeeks(4));
    }

    @Test
    void unknownBoardsLeadHome() {
        navigate("board/999999/timeline", BoardsView.class);
    }

    private CardSnapshot card(long id) {
        return service.findBoard(board.id()).orElseThrow().lanes().stream().flatMap(lane -> lane.cards().stream())
                .filter(card -> card.id() == id).findFirst().orElseThrow();
    }
}
