package dev.gatzka.flowboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CumulativeFlow;
import dev.gatzka.flowboard.domain.LaneDailyCount;
import dev.gatzka.flowboard.repository.LaneDailyCountRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FlowSnapshotServiceTest {

    @Autowired
    private BoardService boardService;

    @Autowired
    private FlowSnapshotService snapshots;

    @Autowired
    private LaneDailyCountRepository counts;

    private BoardSnapshot board;

    @BeforeEach
    void seed() {
        boardService.listBoards().forEach(existing -> boardService.deleteBoard(existing.id(), "Seeder"));
        counts.deleteAll();
        board = boardService.createBoard(new BoardDraft("Flow", null), "Seeder");
    }

    @Test
    void everyBoardChangeRefreshesTodaysRowPerLane() {
        boardService.createCard(board.lanes().get(0).id(), CardDraft.titled("one"), "Seeder");
        boardService.createCard(board.lanes().get(0).id(), CardDraft.titled("two"), "Seeder");

        assertThat(counts.findAll()).filteredOn(row -> row.getBoardId().equals(board.id())).hasSize(4);
        assertThat(counts.findByLaneIdAndDay(board.lanes().get(0).id(), LocalDate.now()).orElseThrow().getCards()).isEqualTo(2);
        assertThat(counts.findByLaneIdAndDay(board.lanes().get(1).id(), LocalDate.now()).orElseThrow().getCards()).isZero();

        CumulativeFlow flow = snapshots.cumulativeFlow(board.id(), 7);
        assertThat(flow.days()).hasSize(7).endsWith(LocalDate.now());
        assertThat(flow.series()).hasSize(4);
        assertThat(flow.series().getFirst().counts()).endsWith(2);
        assertThat(flow.series().getLast().done()).isTrue();
        assertThat(flow.recordedDays()).isEqualTo(1);
        assertThat(flow.hasEnoughData()).as("one day is a spike, not a trend").isFalse();
    }

    @Test
    void missingDaysCarryTheLastKnownValueForward() {
        long backlog = board.lanes().get(0).id();
        LocalDate today = LocalDate.now();
        counts.deleteAll(); // drop the rows written when the board was created
        counts.save(new LaneDailyCount(board.id(), backlog, today.minusDays(5), 3));
        counts.save(new LaneDailyCount(board.id(), backlog, today.minusDays(2), 5));

        CumulativeFlow flow = snapshots.cumulativeFlow(board.id(), 7);

        assertThat(flow.series().getFirst().counts()).containsExactly(0, 3, 3, 3, 5, 5, 5);
        assertThat(flow.maxTotal()).isEqualTo(5);
        assertThat(flow.recordedDays()).isEqualTo(2);
        assertThat(flow.hasEnoughData()).isTrue();
    }

    @Test
    void deletingTheBoardDropsItsRows() {
        boardService.createCard(board.lanes().get(0).id(), CardDraft.titled("x"), "Seeder");
        assertThat(counts.findAll()).isNotEmpty();

        boardService.deleteBoard(board.id(), "Seeder");

        assertThat(counts.findAll()).filteredOn(row -> row.getBoardId().equals(board.id())).isEmpty();
    }

    @Test
    void scheduledRunRecordsEveryBoard() {
        BoardSnapshot other = boardService.createBoard(new BoardDraft("Other", null), "Seeder");
        counts.deleteAll();

        snapshots.recordAllBoards();

        assertThat(counts.findAll()).extracting(LaneDailyCount::getBoardId).contains(board.id(), other.id());
    }
}
