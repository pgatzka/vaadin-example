package dev.gatzka.flowboard.ui.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.textfield.TextArea;
import dev.gatzka.flowboard.assistant.FakeAssistant;
import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.service.BoardService;
import dev.gatzka.flowboard.ui.board.BoardView;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(FakeAssistant.class)
class AssistantUiTest extends SpringBrowserlessTest {

    @Autowired
    private BoardService service;

    @Autowired
    private FakeAssistant.Client fake;

    private BoardSnapshot board;

    @BeforeEach
    void seed() {
        fake.failure = null;
        service.listBoards().forEach(existing -> service.deleteBoard(existing.id(), "Seeder"));
        board = service.createBoard(new BoardDraft("Assisted", null), "Seeder");
    }

    @Test
    void suggestedStepsAreAddedToTheChecklist() {
        CardSnapshot card = service.createCard(board.lanes().get(0).id(), CardDraft.titled("Needs steps"), "Seeder");
        navigate(BoardView.pathTo(board.id(), card.id()), BoardView.class);

        test($(Button.class).withText("Suggest steps").single()).click();
        SuggestChecklistDialog dialog = $(SuggestChecklistDialog.class).single();
        await().pollInSameThread().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            roundTrip();
            assertThat($(Button.class, dialog).withText("Add selected").single().isEnabled()).isTrue();
        });
        CheckboxGroup<String> steps = $(CheckboxGroup.class, dialog).single();
        steps.deselect("Refactor");
        test($(Button.class, dialog).withText("Add selected").single()).click();

        assertThat(service.findBoard(board.id()).orElseThrow().lanes().get(0).cards().getFirst().checklist())
                .extracting(item -> item.text()).containsExactly("Write the test", "Make it pass");
    }

    @Test
    void standupSummaryIsShownAsMarkdown() {
        navigate(BoardView.class, Map.of(BoardView.BOARD_ID, Long.toString(board.id())));

        test($(MenuItem.class).withText("Standup summary").single()).click();
        StandupDialog dialog = $(StandupDialog.class).single();

        await().pollInSameThread().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            roundTrip();
            assertThat(dialog.markdown()).contains("Mara shipped the sync");
        });
    }

    @Test
    void notesBecomeCardsInTheChosenLane() {
        navigate(BoardView.class, Map.of(BoardView.BOARD_ID, Long.toString(board.id())));

        test($(MenuItem.class).withText("Cards from notes…").single()).click();
        NotesToCardsDialog dialog = $(NotesToCardsDialog.class).single();
        test($(TextArea.class, dialog).withLabel("Notes").single()).setValue("Order the tiles. Call the plumber.");
        test($(Button.class, dialog).withText("Extract cards").single()).click();
        await().pollInSameThread().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            roundTrip();
            assertThat($(Button.class, dialog).withText("Create selected").single().isEnabled()).isTrue();
        });
        test($(Button.class, dialog).withText("Create selected").single()).click();

        assertThat(service.findBoard(board.id()).orElseThrow().lanes().get(0).cards())
                .extracting(CardSnapshot::title).containsExactly("Order tiles", "Call the plumber");
    }

    @Test
    void failuresAreShownInsteadOfSwallowed() {
        fake.failure = new dev.gatzka.flowboard.assistant.AssistantException("The assistant is busy right now.");
        navigate(BoardView.class, Map.of(BoardView.BOARD_ID, Long.toString(board.id())));

        test($(MenuItem.class).withText("Standup summary").single()).click();
        StandupDialog dialog = $(StandupDialog.class).single();

        await().pollInSameThread().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            roundTrip();
            assertThat($(com.vaadin.flow.component.html.Span.class, dialog).withClassName("danger").all()).hasSize(1);
        });
    }
}
