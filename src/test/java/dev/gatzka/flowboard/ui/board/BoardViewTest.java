package dev.gatzka.flowboard.ui.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.TextField;
import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.Priority;
import dev.gatzka.flowboard.service.BoardService;
import dev.gatzka.flowboard.ui.boards.BoardsView;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BoardViewTest extends SpringBrowserlessTest {

    private static final String SEEDER = "Seeder";

    @Autowired
    private BoardService service;

    private BoardSnapshot board;

    @BeforeEach
    void seedBoard() {
        service.listBoards().forEach(existing -> service.deleteBoard(existing.id(), SEEDER));
        board = service.createBoard(new BoardDraft("UI board", "For the view tests"), SEEDER);
    }

    @Test
    void rendersEveryLaneWithItsName() {
        openBoard();

        assertThat($(LaneComponent.class).all()).extracting(lane -> lane.lane().name())
                .containsExactly("Backlog", "In progress", "Review", "Done");
    }

    @Test
    void addingCardThroughTheDialogShowsItInTheLane() {
        openBoard();
        LaneComponent backlog = $(LaneComponent.class).first();

        test($(Button.class, backlog).withText("Add card").single()).click();
        CardEditorDialog dialog = $(CardEditorDialog.class).single();
        test($(TextField.class, dialog).withLabel("Title").single()).setValue("Write view tests");
        test($(Button.class, dialog).withText("Add card").single()).click();

        assertThat($(CardEditorDialog.class).all()).as("dialog closes after saving").isEmpty();
        assertThat(visibleCardTitles()).containsExactly("Write view tests");
        assertThat(service.findBoard(board.id()).orElseThrow().lanes().get(0).cards())
                .extracting(CardSnapshot::title).containsExactly("Write view tests");
    }

    @Test
    void cardWithoutTitleIsRejected() {
        openBoard();
        test($(Button.class, $(LaneComponent.class).first()).withText("Add card").single()).click();
        CardEditorDialog dialog = $(CardEditorDialog.class).single();

        test($(Button.class, dialog).withText("Add card").single()).click();

        assertThat(dialog.isOpened()).isTrue();
        assertThat($(TextField.class, dialog).withLabel("Title").single().isInvalid()).isTrue();
        assertThat(visibleCardTitles()).isEmpty();
    }

    @Test
    void editingCardUpdatesIt() {
        CardSnapshot card = createCard("Draft title");
        openBoard();

        test($(CardComponent.class).single()).click();
        CardEditorDialog dialog = $(CardEditorDialog.class).single();
        test($(TextField.class, dialog).withLabel("Title").single()).setValue("Final title");
        test($(Button.class, dialog).withText("Save").single()).click();

        assertThat(visibleCardTitles()).containsExactly("Final title");
        assertThat(service.findBoard(board.id()).orElseThrow().lane(card.laneId()).orElseThrow().cards())
                .extracting(CardSnapshot::title).containsExactly("Final title");
    }

    @Test
    void filterHidesCardsThatDoNotMatch() {
        createCard("Alpha release");
        createCard("Beta feedback");
        openBoard();

        test($(TextField.class).withPlaceholder("Filter cards").single()).setValue("beta");

        assertThat(visibleCardTitles()).containsExactly("Beta feedback");
    }

    @Test
    void changesMadeElsewhereArePushedIntoTheView() {
        openBoard();
        assertThat(visibleCardTitles()).isEmpty();

        createCard("Added from another session");

        // The push arrives on a background thread; roundTrip() must run on the thread owning the UI.
        await().pollInSameThread().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            roundTrip();
            assertThat(visibleCardTitles()).containsExactly("Added from another session");
        });
    }

    @Test
    void unknownBoardFallsBackToTheBoardList() {
        navigate("board/987654321", BoardsView.class);

        assertThat(getCurrentView()).isInstanceOf(BoardsView.class);
    }

    private void openBoard() {
        navigate(BoardView.class, Map.of(BoardView.BOARD_ID, Long.toString(board.id())));
    }

    private CardSnapshot createCard(String title) {
        long backlog = board.lanes().get(0).id();
        return service.createCard(backlog, new CardDraft(title, null, Priority.MEDIUM, null, null, Set.of()), SEEDER);
    }

    private List<String> visibleCardTitles() {
        return $(CardComponent.class).all().stream()
                .filter(Component::isVisible)
                .map(component -> component.card().title())
                .toList();
    }
}
