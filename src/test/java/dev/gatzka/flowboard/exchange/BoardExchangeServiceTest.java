package dev.gatzka.flowboard.exchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardTemplateDraft;
import dev.gatzka.flowboard.domain.CardFilter;
import dev.gatzka.flowboard.domain.RuleAction;
import java.util.List;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.LaneDraft;
import dev.gatzka.flowboard.domain.LaneSnapshot;
import dev.gatzka.flowboard.domain.Priority;
import dev.gatzka.flowboard.service.ActivityLog;
import dev.gatzka.flowboard.service.BoardService;
import dev.gatzka.flowboard.service.ChecklistService;
import dev.gatzka.flowboard.service.CommentService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BoardExchangeServiceTest {

    private static final String ACTOR = "Tester";

    @Autowired
    private BoardService boardService;

    @Autowired
    private ChecklistService checklistService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private BoardExchangeService exchange;

    @Autowired
    private ActivityLog activityLog;

    @Autowired
    private dev.gatzka.flowboard.service.CardTemplateService templateService;

    @Autowired
    private dev.gatzka.flowboard.service.SavedViewService viewService;

    @Autowired
    private dev.gatzka.flowboard.service.AutomationService automationService;

    private BoardSnapshot board;

    @BeforeEach
    void createRichBoard() {
        boardService.listBoards().forEach(existing -> boardService.deleteBoard(existing.id(), ACTOR));
        board = boardService.createBoard(new BoardDraft("Roundtrip", "Exported and imported"), ACTOR);
        boardService.addLane(board.id(), new LaneDraft("Blocked", 1, false), ACTOR);
        long backlog = board.lanes().get(0).id();
        CardSnapshot first = boardService.createCard(backlog, new CardDraft("First", "With **markdown**", Priority.HIGH,
                LocalDate.of(2030, 5, 6), "Mara", Set.of("ui", "feature"), null), ACTOR);
        boardService.createCard(backlog, CardDraft.titled("Second"), ACTOR);
        boardService.createCard(board.lanes().get(3).id(), CardDraft.titled("Finished"), ACTOR);
        CardSnapshot withItems = checklistService.addItem(first.id(), "step one", ACTOR);
        checklistService.setDone(withItems.checklist().getFirst().id(), true, ACTOR);
        checklistService.addItem(first.id(), "step two", ACTOR);
        commentService.add(first.id(), "Jonas", "Looks fine");
        templateService.save(board.id(), new CardTemplateDraft("Bug", null, "Steps", Priority.URGENT, null, Set.of("bug"), 1, List.of("Reproduce", "Fix")), ACTOR);
        viewService.save(board.id(), "Hot", CardFilter.none().withPriority(Priority.HIGH).withTag("ui"), ACTOR);
        automationService.addRule(board.lanes().get(2).id(), RuleAction.ASSIGN, "Priya", ACTOR);
    }

    @Test
    void exportCapturesTheWholeBoardInOrder() {
        BoardExport export = exchange.export(board.id());

        assertThat(export.version()).isEqualTo(BoardExport.CURRENT_VERSION);
        assertThat(export.name()).isEqualTo("Roundtrip");
        assertThat(export.lanes()).extracting(BoardExport.LaneExport::name)
                .containsExactly("Backlog", "In progress", "Review", "Done", "Blocked");
        assertThat(export.lanes().get(4).wipLimit()).isEqualTo(1);
        assertThat(export.lanes().get(3).done()).isTrue();
        assertThat(export.cardCount()).isEqualTo(3);

        BoardExport.CardExport first = export.lanes().get(0).cards().get(0);
        assertThat(first.title()).isEqualTo("First");
        assertThat(first.tags()).containsExactly("feature", "ui");
        assertThat(first.checklist()).extracting(BoardExport.ChecklistItemExport::text).containsExactly("step one", "step two");
        assertThat(first.checklist()).extracting(BoardExport.ChecklistItemExport::done).containsExactly(true, false);
        assertThat(first.comments()).extracting(BoardExport.CommentExport::author).containsExactly("Jonas");
    }

    @Test
    void jsonRoundTripReproducesTheBoard() {
        byte[] json = exchange.toJson(exchange.export(board.id()));
        assertThat(new String(json, StandardCharsets.UTF_8)).contains("\"name\" : \"Roundtrip\"").contains("2030-05-06");

        BoardSnapshot imported = exchange.importBoard(exchange.parse(json), ACTOR);

        assertThat(imported.id()).isNotEqualTo(board.id());
        assertThat(imported.name()).isEqualTo("Roundtrip");
        assertThat(imported.lanes()).extracting(LaneSnapshot::name).containsExactly("Backlog", "In progress", "Review", "Done", "Blocked");
        assertThat(imported.lanes()).extracting(LaneSnapshot::position).containsExactly(0, 1, 2, 3, 4);
        assertThat(imported.cardCount()).isEqualTo(3);

        CardSnapshot first = imported.lanes().get(0).cards().get(0);
        assertThat(first.title()).isEqualTo("First");
        assertThat(first.description()).isEqualTo("With **markdown**");
        assertThat(first.priority()).isEqualTo(Priority.HIGH);
        assertThat(first.dueDate()).isEqualTo(LocalDate.of(2030, 5, 6));
        assertThat(first.assignee()).isEqualTo("Mara");
        assertThat(first.tags()).containsExactly("feature", "ui");
        assertThat(first.checklist()).extracting(c -> c.done()).containsExactly(true, false);
        assertThat(first.comments()).extracting(c -> c.text()).containsExactly("Looks fine");
        assertThat(imported.lanes().get(0).cards().get(1).position()).isEqualTo(1);
        assertThat(activityLog.recent(imported.id()).getFirst().message()).isEqualTo("imported board \"Roundtrip\" with 3 cards");
        assertThat(boardService.listBoards()).hasSize(2);

        assertThat(new String(json, StandardCharsets.UTF_8)).contains("\"templates\"").contains("\"views\"").contains("\"rules\"");
        assertThat(templateService.forBoard(imported.id())).hasSize(1);
        assertThat(templateService.forBoard(imported.id()).getFirst().draft().checklist()).containsExactly("Reproduce", "Fix");
        assertThat(templateService.forBoard(imported.id()).getFirst().draft().priority()).isEqualTo(Priority.URGENT);
        assertThat(viewService.forBoard(imported.id())).extracting(v -> v.name()).containsExactly("Hot");
        assertThat(viewService.forBoard(imported.id()).getFirst().filter()).isEqualTo(CardFilter.none().withPriority(Priority.HIGH).withTag("ui"));
        assertThat(automationService.rulesFor(imported.lanes().get(2).id())).extracting(r -> r.describe()).containsExactly("Assign to Priya");
        assertThat(automationService.rulesFor(imported.lanes().get(0).id())).isEmpty();
    }

    @Test
    void importsTrelloExportsAsNewBoards() throws java.io.IOException {
        byte[] bytes;
        try (var in = getClass().getResourceAsStream("/trello-board.json")) {
            bytes = in.readAllBytes();
        }

        BoardExport parsed = exchange.parse(bytes);
        BoardSnapshot imported = exchange.importBoard(parsed, "Importer");

        assertThat(imported.name()).isEqualTo("Website relaunch");
        assertThat(imported.lanes()).extracting(LaneSnapshot::name).containsExactly("To Do", "Doing", "Done ✓");
        assertThat(imported.lanes().get(2).done()).isTrue();
        assertThat(imported.lanes().getFirst().cards()).extracting(CardSnapshot::title, CardSnapshot::assignee, CardSnapshot::priority)
                .containsExactly(tuple("Fix the header", "Mara K.", Priority.HIGH), tuple("Write copy", "jonas", Priority.MEDIUM));
        assertThat(imported.lanes().getFirst().cards().getFirst().checklist()).hasSize(2);
        assertThat(imported.lanes().getFirst().cards().getFirst().comments()).hasSize(2);
    }

    @Test
    void rejectsFilesThatAreNotBoards() {
        assertThatThrownBy(() -> exchange.parse("not json".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidBoardFileException.class).hasMessageContaining("not valid JSON");
        assertThatThrownBy(() -> exchange.parse("{\"version\": 99, \"name\": \"x\", \"lanes\": []}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidBoardFileException.class).hasMessageContaining("version 99");
        assertThatThrownBy(() -> exchange.parse("{\"version\": 1}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidBoardFileException.class).hasMessageContaining("does not describe a board");
        assertThat(exchange.parse("{\"name\": \"Short form\"}".getBytes(StandardCharsets.UTF_8)).lanes())
                .as("version and lanes may be left out").isEmpty();
    }
}
