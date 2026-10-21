package dev.gatzka.flowboard.ui.boards;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.TextField;
import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.BoardSummary;
import dev.gatzka.flowboard.service.BoardService;
import dev.gatzka.flowboard.ui.board.BoardView;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BoardsViewTest extends SpringBrowserlessTest {

    @Autowired
    private BoardService service;

    /**
     * The test environment navigates to the root view (this one) as soon as it is initialised,
     * so the database has to be clean before that happens, not merely before the test method.
     */
    @BeforeEach
    @Override
    protected void initVaadinEnvironment() {
        service.listBoards().forEach(existing -> service.deleteBoard(existing.id(), "Seeder"));
        super.initVaadinEnvironment();
    }

    @Test
    void showsEmptyStateWithoutBoards() {
        navigate(BoardsView.class);

        assertThat($(BoardTile.class).all()).isEmpty();
        assertThat($(Button.class).withText("Create a board").all()).hasSize(1);
    }

    @Test
    void showsOneTilePerBoard() {
        BoardSnapshot alpha = service.createBoard(new BoardDraft("Alpha", null), "Seeder");
        service.createBoard(new BoardDraft("Beta", "Second board"), "Seeder");

        // The list is already open from environment setup; leave and come back for a fresh render.
        navigate(BoardView.class, Map.of(BoardView.BOARD_ID, Long.toString(alpha.id())));
        navigate(BoardsView.class);

        assertThat($(BoardTile.class).all()).hasSize(2);
    }

    @Test
    void creatingBoardOpensIt() {
        navigate(BoardsView.class);

        test($(Button.class).withText("New board").single()).click();
        BoardDialog dialog = $(BoardDialog.class).single();
        test($(TextField.class, dialog).withLabel("Name").single()).setValue("Roadmap");
        test($(Button.class, dialog).withText("Create board").single()).click();

        assertThat(getCurrentView()).isInstanceOf(BoardView.class);
        assertThat(service.listBoards()).extracting(BoardSummary::name).containsExactly("Roadmap");
    }
}
