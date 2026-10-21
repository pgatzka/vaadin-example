package dev.gatzka.flowboard.ui.digest;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.markdown.Markdown;
import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.service.BoardService;
import dev.gatzka.flowboard.ui.FlowboardUiTest;
import dev.gatzka.flowboard.ui.board.BoardView;
import dev.gatzka.flowboard.ui.boards.BoardsView;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "mara")
class DigestViewTest extends FlowboardUiTest {

    @Autowired
    private BoardService service;

    @Test
    void showsTheWeekAndWalksBackwards() {
        BoardSnapshot board = service.createBoard(new BoardDraft("Digested", null), "Mara");
        service.createCard(board.lanes().get(0).id(), CardDraft.titled("Fresh"), "Mara");

        DigestView view = navigate(DigestView.class, Map.of(BoardView.BOARD_ID, Long.toString(board.id())));
        assertThat(view.digest().to()).isEqualTo(LocalDate.now());
        assertThat($(Markdown.class).single().getContent()).contains("# Digested · week of ").contains("- Fresh");
        assertThat($(Button.class).withId("copy-digest").all()).hasSize(1);

        test($(Button.class).withCondition(b -> "Previous week".equals(b.getAriaLabel().orElse(null))).single()).click();
        assertThat(view.digest().to()).isEqualTo(LocalDate.now().minusWeeks(1));
        assertThat($(Markdown.class).single().getContent()).contains("_Nobody made changes this week._");
    }

    @Test
    void acceptsAWeekInTheUrlAndRejectsUnknownBoards() {
        BoardSnapshot board = service.createBoard(new BoardDraft("Digested", null), "Mara");
        DigestView view = navigate(DigestView.class, Map.of(BoardView.BOARD_ID, Long.toString(board.id()), DigestView.WEEK, "2026-01-07"));
        assertThat(view.digest().from()).isEqualTo(LocalDate.of(2026, 1, 1));

        navigate("board/999999/digest", BoardsView.class);
    }
}
