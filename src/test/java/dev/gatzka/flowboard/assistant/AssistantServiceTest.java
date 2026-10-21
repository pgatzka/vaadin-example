package dev.gatzka.flowboard.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.Priority;
import dev.gatzka.flowboard.service.ActivityLog;
import dev.gatzka.flowboard.service.BoardService;
import dev.gatzka.flowboard.service.ChecklistService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(FakeAssistant.class)
class AssistantServiceTest {

    @Autowired
    private AssistantService assistant;

    @Autowired
    private FakeAssistant.Client fake;

    @Autowired
    private BoardService boardService;

    @Autowired
    private ChecklistService checklistService;

    @Autowired
    private ActivityLog activityLog;

    private BoardSnapshot board;
    private CardSnapshot card;

    @BeforeEach
    void seed() {
        fake.failure = null;
        fake.prompts.clear();
        boardService.listBoards().forEach(existing -> boardService.deleteBoard(existing.id(), "Seeder"));
        board = boardService.createBoard(new BoardDraft("Assisted", null), "Seeder");
        card = boardService.createCard(board.lanes().get(1).id(),
                new CardDraft("Ship v1", "Everything for launch", Priority.HIGH, null, "Mara", Set.of("release"), 5), "Seeder");
    }

    @Test
    void isAvailableWhenAClientIsConfigured() {
        assertThat(assistant.isAvailable()).isTrue();
    }

    @Test
    void checklistSuggestionsAreParsedFromFencedJsonAndIncludeContext() {
        CardSnapshot withItem = checklistService.addItem(card.id(), "Already there", "Seeder");

        List<String> steps = assistant.suggestChecklist(withItem, board);

        assertThat(steps).containsExactly("Write the test", "Make it pass", "Refactor");
        assertThat(fake.prompts.getFirst()).contains("Ship v1").contains("Everything for launch").contains("Already there");
    }

    @Test
    void emptyOrMalformedChecklistAnswersAreReported() {
        fake.checklistAnswer = "I cannot help with that.";
        assertThatThrownBy(() -> assistant.suggestChecklist(card, board)).isInstanceOf(AssistantException.class)
                .hasMessageContaining("unexpected format");

        fake.checklistAnswer = "[\"\", 42]";
        assertThatThrownBy(() -> assistant.suggestChecklist(card, board)).isInstanceOf(AssistantException.class)
                .hasMessageContaining("did not come up");
    }

    @Test
    void standupPromptDescribesLanesCardsAndRecentActivity() {
        BoardSnapshot current = boardService.findBoard(board.id()).orElseThrow();
        String summary = assistant.standupSummary(current, activityLog.recent(board.id()));

        assertThat(summary).startsWith("## Done in the last 24 hours");
        String prompt = fake.prompts.getFirst();
        assertThat(prompt).contains("Lane \"In progress\"").contains("Ship v1 [Mara]").contains("priority high")
                .contains("(done lane)").contains("added \"Ship v1\" to In progress");
    }

    @Test
    void cardsFromNotesAreValidatedAndDefaulted() {
        List<CardDraft> drafts = assistant.cardsFromNotes("Order the tiles, call the plumber", board);

        assertThat(drafts).hasSize(2);
        assertThat(drafts.get(0).title()).isEqualTo("Order tiles");
        assertThat(drafts.get(0).priority()).isEqualTo(Priority.URGENT);
        assertThat(drafts.get(0).assignee()).isEqualTo("Mara");
        assertThat(drafts.get(0).description()).isEqualTo("Two boxes.");
        assertThat(drafts.get(1).title()).isEqualTo("Call the plumber");
        assertThat(drafts.get(1).priority()).as("unknown priority falls back to medium").isEqualTo(Priority.MEDIUM);
        assertThat(assistant.cardsFromNotes("   ", board)).isEmpty();
        assertThat(fake.prompts).hasSize(1);
    }

    @Test
    void clientFailuresSurfaceAsAssistantExceptions() {
        fake.failure = new AssistantException("The assistant is busy right now.");
        assertThatThrownBy(() -> assistant.standupSummary(board, List.of())).isInstanceOf(AssistantException.class)
                .hasMessageContaining("busy");
    }
}
