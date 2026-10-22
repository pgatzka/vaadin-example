package dev.gatzka.flowboard.ui.walk;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.KeyPressEvent;
import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.Priority;
import dev.gatzka.flowboard.service.BoardService;
import dev.gatzka.flowboard.ui.FlowboardUiTest;
import dev.gatzka.flowboard.ui.boards.BoardsView;
import java.time.Duration;
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
class WalkViewTest extends FlowboardUiTest {

    @Autowired
    private BoardService service;

    private BoardSnapshot board;

    @BeforeEach
    void seed() {
        service.listBoards().forEach(existing -> service.deleteBoard(existing.id(), "Seeder"));
        board = service.createBoard(new BoardDraft("Standup", null), "Seeder");
        long backlog = board.lanes().get(0).id();
        long doing = board.lanes().get(1).id();
        long done = board.lanes().get(3).id();
        card(backlog, "Backlog item", "Priya");
        card(doing, "Jonas doing", "Jonas");
        card(doing, "Priya doing", "Priya");
        card(backlog, "Nobody's", null);
        card(done, "Finished", "Jonas");
    }

    @Test
    void walksCardsInLaneOrPersonOrderSkippingDoneByDefault() {
        navigate(WalkView.class, Map.of(WalkView.BOARD_ID, Long.toString(board.id())));

        assertThat(title()).isEqualTo("Backlog item");
        assertThat($(Span.class).withId("walk-progress").single().getText()).isEqualTo("1 of 4");
        assertThat($(Button.class).withId("walk-previous").single().isEnabled()).isFalse();

        test($(Button.class).withId("walk-next").single()).click();
        assertThat(title()).isEqualTo("Nobody's");
        test($(Button.class).withId("walk-next").single()).click();
        test($(Button.class).withId("walk-next").single()).click();
        assertThat(title()).isEqualTo("Priya doing");
        assertThat($(Button.class).withId("walk-next").single().isEnabled()).isFalse();
        assertThat($(Span.class).withId("walk-progress").single().getText()).isEqualTo("4 of 4");

        test($(Select.class).withId("walk-order").single(), WalkView.Order.class).selectItem("By person");
        assertThat(title()).as("stays on the same card when the order changes").isEqualTo("Priya doing");
        assertThat(WalkView.stops(board(), WalkView.Order.PERSON, true)).extracting(stop -> stop.card().title())
                .containsExactly("Jonas doing", "Backlog item", "Priya doing", "Nobody's");

        test($(Checkbox.class).withId("walk-skip-done").single()).click();
        assertThat(WalkView.stops(board(), WalkView.Order.LANE, false)).extracting(stop -> stop.card().title()).contains("Finished");
        assertThat($(Span.class).withId("walk-progress").single().getText()).as("Finished joins Jonas' cards ahead of Priya's").isEqualTo("4 of 5");
    }

    @Test
    void notesBecomeCommentsAndAdvanceMovesTheCardOn() {
        navigate(WalkView.class, Map.of(WalkView.BOARD_ID, Long.toString(board.id())));
        assertThat(title()).isEqualTo("Backlog item");

        TextField note = $(TextField.class).withId("walk-note").single();
        test(note).setValue("Blocked on the API team");
        ComponentUtil.fireEvent(note, new KeyPressEvent(note, true, "Enter", "Enter", 0, false, false, false, false, false, false));
        pump();
        CardSnapshot first = board().lanes().get(0).cards().getFirst();
        assertThat(first.comments()).extracting(c -> c.text()).containsExactly("Blocked on the API team");

        Button advance = $(Button.class).withId("walk-advance").single();
        assertThat(advance.getText()).isEqualTo("Move to In progress");
        test(advance).click();
        org.awaitility.Awaitility.await().pollInSameThread().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            pump();
            assertThat(board().lanes().get(1).cards()).extracting(CardSnapshot::title).contains("Backlog item");
            assertThat(title()).as("follows the moved card").isEqualTo("Backlog item");
            assertThat($(Badge.class).withClassName("walk__lane").single().getText()).isEqualTo("In progress");
        });
    }

    @Test
    void boardsOutOfReachLeadHome() {
        navigate("board/999999/walk", BoardsView.class);
    }

    private String title() {
        return $(H1.class).withClassName("walk__title").single().getText();
    }

    private BoardSnapshot board() {
        return service.findBoard(board.id()).orElseThrow();
    }

    private void card(long laneId, String title, String assignee) {
        service.createCard(laneId, new CardDraft(title, null, Priority.MEDIUM, null, assignee, Set.of(), null), "Seeder");
    }
}
