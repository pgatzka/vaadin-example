package dev.gatzka.flowboard.service;

import dev.gatzka.flowboard.domain.CumulativeFlow;
import dev.gatzka.flowboard.domain.CumulativeFlow.Series;
import dev.gatzka.flowboard.domain.Lane;
import dev.gatzka.flowboard.domain.LaneDailyCount;
import dev.gatzka.flowboard.repository.BoardRepository;
import dev.gatzka.flowboard.repository.CardRepository;
import dev.gatzka.flowboard.repository.LaneDailyCountRepository;
import dev.gatzka.flowboard.repository.LaneRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Keeps a per-day count of cards per lane. Today's row is refreshed after every change to the
 * board and once more shortly after midnight, so the series always reflects end-of-day state.
 */
@Service
public class FlowSnapshotService {

    static final int DEFAULT_DAYS = 30;

    private final BoardRepository boards;
    private final LaneRepository lanes;
    private final CardRepository cards;
    private final LaneDailyCountRepository counts;
    private final Clock clock;

    FlowSnapshotService(BoardRepository boards, LaneRepository lanes, CardRepository cards, LaneDailyCountRepository counts,
            Clock clock) {
        this.boards = boards;
        this.lanes = lanes;
        this.cards = cards;
        this.counts = counts;
        this.clock = clock;
    }

    /** Writes or updates today's row for every lane of the board. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordToday(long boardId) {
        LocalDate today = LocalDate.now(clock);
        for (Lane lane : lanes.findAllByBoardIdOrderByPositionAsc(boardId)) {
            int live = (int) cards.countByLaneIdAndArchivedAtIsNull(lane.getId());
            counts.findByLaneIdAndDay(lane.getId(), today).ifPresentOrElse(
                    row -> row.setCards(live),
                    () -> counts.save(new LaneDailyCount(boardId, lane.getId(), today, live)));
        }
    }

    /**
     * Runs after the change committed. The listener needs its own transaction: at that point the
     * committing transaction is over and writes made in it would be lost.
     */
    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBoardChanged(BoardChangedEvent event) {
        if (event.kind() == BoardChangedEvent.Kind.DELETED) {
            forget(event.boardId());
        } else {
            recordToday(event.boardId());
        }
    }

    /** Captures the state at the start of a new day for every board. */
    @Scheduled(cron = "0 5 0 * * *")
    public void recordAllBoards() {
        boards.findAll().forEach(board -> recordToday(board.getId()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void forget(long boardId) {
        counts.deleteAllByBoardId(boardId);
    }

    /** Daily counts for the last {@value #DEFAULT_DAYS} days; days without a row carry the previous value. */
    @Transactional(readOnly = true)
    public CumulativeFlow cumulativeFlow(long boardId) {
        return cumulativeFlow(boardId, DEFAULT_DAYS);
    }

    @Transactional(readOnly = true)
    public CumulativeFlow cumulativeFlow(long boardId, int days) {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.minusDays(days - 1L);
        List<Lane> boardLanes = lanes.findAllByBoardIdOrderByPositionAsc(boardId);
        Map<Long, Map<LocalDate, Integer>> byLane = new HashMap<>();
        for (LaneDailyCount row : counts.findAllByBoardIdAndDayGreaterThanEqualOrderByDayAsc(boardId, from)) {
            byLane.computeIfAbsent(row.getLaneId(), id -> new HashMap<>()).put(row.getDay(), row.getCards());
        }
        List<LocalDate> dayList = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(today); day = day.plusDays(1)) {
            dayList.add(day);
        }
        List<Series> series = new ArrayList<>();
        for (Lane lane : boardLanes) {
            Map<LocalDate, Integer> rows = byLane.getOrDefault(lane.getId(), Map.of());
            List<Integer> values = new ArrayList<>(dayList.size());
            int last = 0;
            for (LocalDate day : dayList) {
                last = rows.getOrDefault(day, last);
                values.add(last);
            }
            series.add(new Series(lane.getName(), lane.isDone(), List.copyOf(values)));
        }
        return new CumulativeFlow(List.copyOf(dayList), List.copyOf(series));
    }
}
