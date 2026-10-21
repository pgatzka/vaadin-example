package dev.gatzka.flowboard.ui.boards;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.TextField;
import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.BoardSummary;
import dev.gatzka.flowboard.domain.BoardTemplate;
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
    void exportDialogOffersADownloadLink() {
        BoardSnapshot alpha = service.createBoard(new BoardDraft("Alpha Board!", null), "Seeder");
        navigate(BoardView.class, Map.of(BoardView.BOARD_ID, Long.toString(alpha.id())));
        navigate(BoardsView.class);

        test($(MenuItem.class, $(BoardTile.class).single()).withText("Export as JSON").single()).click();

        ExportDialog dialog = $(ExportDialog.class).single();
        assertThat($(Anchor.class, dialog).single().getText()).isEqualTo("Download alpha-board.flowboard.json");
        assertThat(ExportDialog.fileName("  ")).isEqualTo("board.flowboard.json");
    }

    @Test
    void homeShowsTotalsAttentionAndActivity() {
        BoardSnapshot alpha = service.createBoard(new BoardDraft("Alpha", null), "Mara");
        service.createCard(alpha.lanes().get(0).id(), new dev.gatzka.flowboard.domain.CardDraft("Late", null, null,
                java.time.LocalDate.now().minusDays(1), null, java.util.Set.of(), null), "Mara");
        navigate(BoardView.class, Map.of(BoardView.BOARD_ID, Long.toString(alpha.id())));
        navigate(BoardsView.class);

        assertThat($(com.vaadin.flow.component.html.Span.class).withClassName("insights__tile-value").all())
                .extracting(span -> span.getText()).containsExactly("1", "1", "0", "0");
        assertThat($(dev.gatzka.flowboard.ui.work.CardLocationRow.class).all()).hasSize(1);
        assertThat($(com.vaadin.flow.component.html.Div.class).withClassName("dashboard__activity-row").all()).isNotEmpty();
    }

    @Test
    void creatingBoardOpensIt() {
        navigate(BoardsView.class);

        test($(Button.class).withText("New board").single()).click();
        BoardDialog dialog = $(BoardDialog.class).single();
        test($(TextField.class, dialog).withLabel("Name").single()).setValue("Roadmap");
        test($(RadioButtonGroup.class, dialog).single(), BoardTemplate.class).selectItem("Personal");
        test($(Button.class, dialog).withText("Create board").single()).click();

        assertThat(getCurrentView()).isInstanceOf(BoardView.class);
        assertThat(service.listBoards()).extracting(BoardSummary::name).containsExactly("Roadmap");
        assertThat(service.findBoard(service.listBoards().getFirst().id()).orElseThrow().lanes())
                .extracting(lane -> lane.name()).containsExactly("To do", "Doing", "Done");
    }
}
