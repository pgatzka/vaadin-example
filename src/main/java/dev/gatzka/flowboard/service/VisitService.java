package dev.gatzka.flowboard.service;

import dev.gatzka.flowboard.domain.Board;
import dev.gatzka.flowboard.domain.BoardVisit;
import dev.gatzka.flowboard.repository.ActivityRepository;
import dev.gatzka.flowboard.repository.BoardRepository;
import dev.gatzka.flowboard.repository.BoardVisitRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Remembers when each user last opened each board, and counts what other people did on a board
 * since then. A board never opened counts everything that ever happened on it.
 */
@Service
@Transactional
public class VisitService {

    private final BoardVisitRepository visits;
    private final BoardRepository boards;
    private final ActivityRepository activities;
    private final Clock clock;

    VisitService(BoardVisitRepository visits, BoardRepository boards, ActivityRepository activities, Clock clock) {
        this.visits = visits;
        this.boards = boards;
        this.activities = activities;
        this.clock = clock;
    }

    /** Records that the user is looking at the board now and returns when they last did, if ever. */
    public Optional<Instant> visit(String username, long boardId) {
        if (username == null) {
            return Optional.empty();
        }
        Optional<BoardVisit> previous = visits.findByUsernameAndBoardId(username, boardId);
        Optional<Instant> before = previous.map(BoardVisit::getVisitedAt);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        previous.ifPresentOrElse(visit -> visit.visitedAt(now), () -> visits.save(new BoardVisit(username, boardId, now)));
        return before;
    }

    @Transactional(readOnly = true)
    public Optional<Instant> lastVisit(String username, long boardId) {
        return username == null ? Optional.empty() : visits.findByUsernameAndBoardId(username, boardId).map(BoardVisit::getVisitedAt);
    }

    /**
     * Activity on each board since the user's last visit, not counting their own, keyed by board id
     * in board order. Boards without news are left out.
     */
    @Transactional(readOnly = true)
    public Map<Long, Long> unreadCounts(String username, String displayName) {
        Map<Long, Instant> lastVisits = username == null ? Map.of() : visits.findAllByUsername(username).stream()
                .collect(Collectors.toMap(BoardVisit::getBoardId, BoardVisit::getVisitedAt));
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (Board board : boards.findAll()) {
            Instant since = lastVisits.getOrDefault(board.getId(), Instant.EPOCH);
            long count = activities.countByBoardIdAndOccurredAtAfterAndActorNot(board.getId(), since, displayName);
            if (count > 0) {
                counts.put(board.getId(), count);
            }
        }
        return counts;
    }

    public void forgetBoard(long boardId) {
        visits.deleteAllByBoardId(boardId);
    }
}
