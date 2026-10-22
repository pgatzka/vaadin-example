package dev.gatzka.flowboard.service;

import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.Health;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Puts a board's health findings together from the board, its forecast and its milestones. */
@Service
@Transactional(readOnly = true)
public class HealthService {

    private final BoardService boards;
    private final InsightsService insights;
    private final MilestoneService milestones;
    private final Clock clock;

    HealthService(BoardService boards, InsightsService insights, MilestoneService milestones, Clock clock) {
        this.boards = boards;
        this.insights = insights;
        this.milestones = milestones;
        this.clock = clock;
    }

    public Health forBoard(long boardId) {
        BoardSnapshot board = boards.findBoard(boardId).orElseThrow(() -> new NotFoundException("Board", boardId));
        return of(board);
    }

    /** The health of a board already loaded, e.g. by the view that shows it. */
    public Health of(BoardSnapshot board) {
        return Health.of(board, LocalDate.now(clock), clock.instant(), insights.forecast(board.id()), milestones.progress(board.id()));
    }
}
