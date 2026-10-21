package dev.gatzka.flowboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.Priority;
import dev.gatzka.flowboard.domain.WeeklyDigest;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DigestServiceTest {

    @Autowired
    private BoardService boardService;

    @Autowired
    private DependencyService dependencies;

    @Autowired
    private DigestService digests;

    @Test
    void summarisesTheWeekAndRendersMarkdown() {
        LocalDate today = LocalDate.now();
        BoardSnapshot board = boardService.createBoard(new BoardDraft("Launch", null), "Mara");
        long backlog = board.lanes().get(0).id();
        long done = board.lanes().get(3).id();
        CardSnapshot shipped = boardService.createCard(backlog,
                new CardDraft("Ship it", null, Priority.HIGH, null, "Mara", Set.of(), 5), "Mara");
        boardService.moveCard(shipped.id(), done, 0, "Mara");
        CardSnapshot late = boardService.createCard(backlog,
                new CardDraft("Late one", null, Priority.MEDIUM, today.minusDays(2), "Jonas", Set.of(), null), "Jonas");
        CardSnapshot soon = boardService.createCard(backlog,
                new CardDraft("Soon", null, Priority.MEDIUM, today.plusDays(3), null, Set.of(), null), "Jonas");
        CardSnapshot waiting = boardService.createCard(backlog, CardDraft.titled("Waiting"), "Mara");
        dependencies.addBlocker(waiting.id(), late.id(), "Mara");
        boardService.createCard(backlog, CardDraft.titled("Automatic"), ActivityLog.SYSTEM_ACTOR);

        WeeklyDigest digest = digests.thisWeek(board.id());

        assertThat(digest.boardName()).isEqualTo("Launch");
        assertThat(digest.from()).isEqualTo(today.minusDays(6));
        assertThat(digest.to()).isEqualTo(today);
        assertThat(digest.completed()).extracting(CardSnapshot::title).containsExactly("Ship it");
        assertThat(digest.pointsCompleted()).isEqualTo(5);
        assertThat(digest.created()).extracting(CardSnapshot::title).containsExactly("Ship it", "Late one", "Soon", "Waiting", "Automatic");
        assertThat(digest.overdue()).extracting(CardSnapshot::title).containsExactly("Late one");
        assertThat(digest.blocked()).extracting(CardSnapshot::title).containsExactly("Waiting");
        assertThat(digest.dueNextWeek()).extracting(CardSnapshot::title).containsExactly("Soon");
        assertThat(digest.changes()).isEqualTo(8);
        assertThat(digest.contributors()).extracting(c -> c.name()).containsExactly("Mara", "Jonas");
        assertThat(digest.isQuiet()).isFalse();

        String markdown = digest.toMarkdown();
        assertThat(markdown).startsWith("# Launch · week of ")
                .contains("8 changes by 2 people. 1 card done (5 points), 5 created, 1 overdue, 1 blocked.")
                .contains("## Done (1)\n\n- Ship it (Mara)")
                .contains("## Overdue (1)\n\n- Late one — due ")
                .contains("## Blocked (1)\n\n- Waiting")
                .contains("## Most active\n\n- Mara: ")
                .contains("- Jonas: ");

        WeeklyDigest lastWeek = digests.weekly(board.id(), today.minusDays(7));
        assertThat(lastWeek.completed()).isEmpty();
        assertThat(lastWeek.changes()).isZero();
        assertThat(lastWeek.overdue()).as("nothing was overdue a week ago").isEmpty();
        assertThat(lastWeek.toMarkdown()).contains("_Nobody made changes this week._").contains("_Nothing here._");
        assertThatThrownBy(() -> digests.thisWeek(999_999L)).isInstanceOf(NotFoundException.class);
    }
}
