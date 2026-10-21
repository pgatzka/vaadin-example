package dev.gatzka.flowboard.ui.boards;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gatzka.flowboard.ui.FlowboardUiTest;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "mara")
class BoardsViewTest extends FlowboardUiTest {

    @Autowired
    private BoardService service;

    @Autowired
    private dev.gatzka.flowboard.service.AccessService access;

    @Autowired
    private dev.gatzka.flowboard.security.AccountService accounts;

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
    void sidebarShowsWhatHappenedSinceTheLastVisit() {
        BoardSnapshot alpha = service.createBoard(new BoardDraft("Alpha", null), "Jonas");
        service.createCard(alpha.lanes().get(0).id(), dev.gatzka.flowboard.domain.CardDraft.titled("News"), "Jonas");
        navigate(BoardsView.class);
        navigate(dev.gatzka.flowboard.ui.work.MyWorkView.class);
        assertThat($(com.vaadin.flow.component.badge.Badge.class).withClassName("nav-unread").single().getText()).isEqualTo("2");

        navigate(BoardView.class, Map.of(BoardView.BOARD_ID, Long.toString(alpha.id())));
        assertThat($(dev.gatzka.flowboard.ui.board.CardComponent.class).withClassName("task-card--changed").all())
                .as("a first visit highlights nothing").isEmpty();
        navigate(BoardsView.class);
        assertThat($(com.vaadin.flow.component.badge.Badge.class).withClassName("nav-unread").all()).isEmpty();

        service.createCard(alpha.lanes().get(0).id(), dev.gatzka.flowboard.domain.CardDraft.titled("Newer"), "Jonas");
        navigate(BoardView.class, Map.of(BoardView.BOARD_ID, Long.toString(alpha.id())));
        assertThat($(dev.gatzka.flowboard.ui.board.CardComponent.class).withClassName("task-card--changed").all())
                .extracting(component -> component.card().title()).containsExactly("Newer");
    }

    @Test
    void membersOnlyBoardsHideFromEverybodyElse() {
        BoardSnapshot open = service.createBoard(new BoardDraft("Open", null), "Mara");
        BoardSnapshot secret = service.createBoard(new BoardDraft("Secret", null), "Jonas");
        if (accounts.find("jonas").isEmpty()) {
            accounts.register("jonas", "Jonas", "longenough");
        }
        if (accounts.find("mara").isEmpty()) {
            accounts.register("mara", "mara", "longenough"); // the mock principal, shown by its login like before
        }
        access.setMembersOnly(secret.id(), true, "jonas", "Jonas");

        navigate(BoardView.class, Map.of(BoardView.BOARD_ID, Long.toString(open.id())));
        navigate(BoardsView.class);
        assertThat($(BoardTile.class).all()).hasSize(1);
        assertThat($(com.vaadin.flow.component.sidenav.SideNavItem.class).all())
                .extracting(com.vaadin.flow.component.sidenav.SideNavItem::getLabel).contains("Open").doesNotContain("Secret");

        navigate("board/" + secret.id(), BoardsView.class);

        access.addMember(secret.id(), "mara", "Jonas");
        navigate(BoardView.class, Map.of(BoardView.BOARD_ID, Long.toString(secret.id())));
        navigate(BoardsView.class);
        assertThat($(BoardTile.class).all()).hasSize(2);
        assertThat($(com.vaadin.flow.component.badge.Badge.class).withClassName("board-tile__private").all()).hasSize(1);
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
