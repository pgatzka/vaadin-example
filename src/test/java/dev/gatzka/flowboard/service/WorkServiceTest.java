package dev.gatzka.flowboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardLocation;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.Priority;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WorkServiceTest {

    @Autowired
    private BoardService boardService;

    @Autowired
    private WorkService workService;

    private BoardSnapshot alpha;
    private BoardSnapshot beta;

    @BeforeEach
    void seed() {
        boardService.listBoards().forEach(existing -> boardService.deleteBoard(existing.id(), "Seeder"));
        alpha = boardService.createBoard(new BoardDraft("Alpha", null), "Seeder");
        beta = boardService.createBoard(new BoardDraft("Beta", null), "Seeder");
        card(alpha, 0, "Alpha task", "mara", "ui");
        card(alpha, 3, "Alpha finished", "Mara");
        card(beta, 1, "Beta task", "MARA", "backend");
        card(beta, 0, "Somebody else", "Jonas", "ui");
        CardSnapshot archived = card(beta, 0, "Archived mara", "Mara");
        boardService.archiveCard(archived.id(), "Seeder");
    }

    @Test
    void assignedCardsSpanBoardsIgnoreCaseAndSkipArchived() {
        assertThat(workService.assignedTo("Mara")).extracting(location -> location.card().title())
                .containsExactly("Alpha task", "Alpha finished", "Beta task");
        assertThat(workService.assignedTo("mara")).extracting(CardLocation::boardName).containsExactly("Alpha", "Alpha", "Beta");
        assertThat(workService.assignedTo("Mara")).extracting(CardLocation::isCompleted).containsExactly(false, true, false);
        assertThat(workService.assignedTo("Nobody")).isEmpty();
    }

    @Test
    void searchMatchesAcrossBoardsAndFields() {
        assertThat(workService.search("ui")).extracting(location -> location.card().title())
                .containsExactly("Alpha task", "Somebody else");
        assertThat(workService.search("jonas")).extracting(CardLocation::laneName).containsExactly("Backlog");
        assertThat(workService.search("archived")).isEmpty();
        assertThat(workService.search("  ")).isEmpty();
    }

    private CardSnapshot card(BoardSnapshot board, int lane, String title, String assignee, String... tags) {
        return boardService.createCard(board.lanes().get(lane).id(),
                new CardDraft(title, null, Priority.MEDIUM, null, assignee, Set.of(tags)), "Seeder");
    }
}
