package dev.gatzka.flowboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.Overview;
import dev.gatzka.flowboard.domain.Priority;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class OverviewServiceTest {

    @Autowired
    private BoardService boardService;

    @Autowired
    private OverviewService overviewService;

    @BeforeEach
    void clear() {
        boardService.listBoards().forEach(existing -> boardService.deleteBoard(existing.id(), "Seeder"));
    }

    @Test
    void emptyInstallationHasAnEmptyOverview() {
        Overview overview = overviewService.overview();
        assertThat(overview.isEmpty()).isTrue();
        assertThat(overview.recentActivity()).isEmpty();
    }

    @Test
    void countsAcrossBoardsAndListsOverdueWorkMostOverdueFirst() {
        LocalDate today = LocalDate.now();
        BoardSnapshot alpha = boardService.createBoard(new BoardDraft("Alpha", null), "Mara");
        BoardSnapshot beta = boardService.createBoard(new BoardDraft("Beta", null), "Mara");
        card(alpha, 0, "Very late", today.minusDays(5));
        card(beta, 1, "Late", today.minusDays(1));
        card(beta, 0, "Soon", today.plusDays(2));
        card(alpha, 3, "Finished", today.minusDays(9));
        card(alpha, 0, "Whenever", null);

        Overview overview = overviewService.overview();

        assertThat(overview.boards()).isEqualTo(2);
        assertThat(overview.openCards()).isEqualTo(4);
        assertThat(overview.overdueCards()).isEqualTo(2);
        assertThat(overview.dueThisWeek()).isEqualTo(1);
        assertThat(overview.completedLast7Days()).isEqualTo(1);
        assertThat(overview.needsAttention()).extracting(location -> location.card().title()).containsExactly("Very late", "Late");
        assertThat(overview.needsAttention().get(1).boardName()).isEqualTo("Beta");
        assertThat(overview.recentActivity()).hasSizeLessThanOrEqualTo(OverviewService.ACTIVITY_LIMIT);
        assertThat(overview.recentActivity().getFirst().entry().message()).isEqualTo("added \"Whenever\" to Backlog");
        assertThat(overview.recentActivity().getFirst().boardName()).isEqualTo("Alpha");
    }

    private void card(BoardSnapshot board, int lane, String title, LocalDate due) {
        boardService.createCard(board.lanes().get(lane).id(), new CardDraft(title, null, Priority.MEDIUM, due, null, Set.of(), null), "Mara");
    }
}
