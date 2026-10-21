package dev.gatzka.flowboard.service;

import dev.gatzka.flowboard.domain.Board;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.repository.BoardRepository;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public read-only links. A shared board is reachable by anyone who knows its token; turning the
 * link off invalidates the token for good, turning it on again mints a new one.
 */
@Service
@Transactional
public class SharingService {

    private static final int TOKEN_BYTES = 18;

    private final BoardRepository boards;
    private final BoardService boardService;
    private final ActivityLog activityLog;
    private final SecureRandom random = new SecureRandom();

    SharingService(BoardRepository boards, BoardService boardService, ActivityLog activityLog) {
        this.boards = boards;
        this.boardService = boardService;
        this.activityLog = activityLog;
    }

    /** The current token of a board, if it is shared. */
    @Transactional(readOnly = true)
    public Optional<String> token(long boardId) {
        return Optional.ofNullable(requireBoard(boardId).getShareToken());
    }

    /** Turns the public link on and returns its token; an already shared board keeps its token. */
    public String enable(long boardId, String actor) {
        Board board = requireBoard(boardId);
        if (!board.isShared()) {
            board.share(newToken());
            activityLog.record(BoardChangedEvent.updated(boardId, actor, "turned the public link on"));
        }
        return board.getShareToken();
    }

    public void disable(long boardId, String actor) {
        Board board = requireBoard(boardId);
        if (board.isShared()) {
            board.unshare();
            activityLog.record(BoardChangedEvent.updated(boardId, actor, "turned the public link off"));
        }
    }

    /** The board behind a public link, or empty when the token is unknown or the link was turned off. */
    @Transactional(readOnly = true)
    public Optional<BoardSnapshot> findShared(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return boards.findByShareToken(token).map(Board::getId).flatMap(boardService::findBoard);
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Board requireBoard(long boardId) {
        return boards.findById(boardId).orElseThrow(() -> new NotFoundException("Board", boardId));
    }
}
