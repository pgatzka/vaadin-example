package dev.gatzka.flowboard.service;

import dev.gatzka.flowboard.domain.Board;
import dev.gatzka.flowboard.domain.BoardAccess;
import dev.gatzka.flowboard.domain.BoardMember;
import dev.gatzka.flowboard.domain.BoardSummary;
import dev.gatzka.flowboard.repository.BoardMemberRepository;
import dev.gatzka.flowboard.repository.BoardRepository;
import dev.gatzka.flowboard.repository.CardRepository;
import dev.gatzka.flowboard.security.AccountService;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who may see which board. Boards are open to every signed-in user unless they are set to
 * members only, in which case only the listed accounts (by login) can find or open them.
 * The public read-only link is unaffected: it is its own, deliberate way in.
 */
@Service
@Transactional
public class AccessService {

    private final BoardRepository boards;
    private final BoardMemberRepository members;
    private final CardRepository cards;
    private final BoardService boardService;
    private final AccountService accounts;
    private final ActivityLog activityLog;
    private final Clock clock;

    AccessService(BoardRepository boards, BoardMemberRepository members, CardRepository cards, BoardService boardService,
            AccountService accounts, ActivityLog activityLog, Clock clock) {
        this.boards = boards;
        this.members = members;
        this.cards = cards;
        this.boardService = boardService;
        this.accounts = accounts;
        this.activityLog = activityLog;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean canAccess(long boardId, String login) {
        return boards.findById(boardId).map(board -> canAccess(board, login)).orElse(false);
    }

    /** Whether the login may see the board holding the card; unknown cards are not accessible. */
    @Transactional(readOnly = true)
    public boolean canAccessCard(long cardId, String login) {
        return cards.findById(cardId).map(card -> canAccess(card.getLane().getBoard(), login)).orElse(false);
    }

    /** The boards the login may see, in creation order. */
    @Transactional(readOnly = true)
    public List<BoardSummary> visibleBoards(String login) {
        Set<Long> allowed = visibleBoardIds(login);
        return boardService.listBoards().stream().filter(board -> allowed.contains(board.id())).toList();
    }

    @Transactional(readOnly = true)
    public Set<Long> visibleBoardIds(String login) {
        Set<Long> memberOf = login == null ? Set.of() : members.findAllByUsername(key(login)).stream()
                .map(BoardMember::getBoardId).collect(Collectors.toSet());
        return boards.findAll().stream()
                .filter(board -> !board.isMembersOnly() || memberOf.contains(board.getId()))
                .map(Board::getId)
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public BoardAccess access(long boardId) {
        Board board = requireBoard(boardId);
        return new BoardAccess(boardId, board.isMembersOnly(),
                members.findAllByBoardIdOrderByUsernameAsc(boardId).stream().map(BoardMember::getUsername).toList());
    }

    /** Restricts the board to members, adding the person doing it so nobody locks themselves out. */
    public BoardAccess setMembersOnly(long boardId, boolean membersOnly, String login, String actor) {
        Board board = requireBoard(boardId);
        if (membersOnly && login != null) {
            ensureMember(boardId, key(login));
        }
        if (board.isMembersOnly() != membersOnly) {
            board.setMembersOnly(membersOnly);
            activityLog.record(BoardChangedEvent.updated(boardId, actor,
                    membersOnly ? "restricted the board to its members" : "opened the board to everyone"));
        }
        return access(boardId);
    }

    /** Adds an existing account as a member. */
    public BoardAccess addMember(long boardId, String username, String actor) {
        requireBoard(boardId);
        String login = key(username);
        if (accounts.find(login).isEmpty()) {
            throw new IllegalArgumentException("There is no account called \"" + username + "\"");
        }
        if (ensureMember(boardId, login)) {
            activityLog.record(BoardChangedEvent.updated(boardId, actor, "added %s to the board".formatted(accounts.displayNameOf(login))));
        }
        return access(boardId);
    }

    /** Removes a member; the last member of a members-only board stays so the board remains reachable. */
    public BoardAccess removeMember(long boardId, String username, String actor) {
        Board board = requireBoard(boardId);
        String login = key(username);
        if (!members.existsByBoardIdAndUsername(boardId, login)) {
            return access(boardId);
        }
        if (board.isMembersOnly() && members.countByBoardId(boardId) <= 1) {
            throw new IllegalArgumentException("A members-only board needs at least one member; open it to everyone first");
        }
        members.deleteByBoardIdAndUsername(boardId, login);
        activityLog.record(BoardChangedEvent.updated(boardId, actor, "removed %s from the board".formatted(accounts.displayNameOf(login))));
        return access(boardId);
    }

    public void forgetBoard(long boardId) {
        members.deleteAllByBoardId(boardId);
    }

    private boolean ensureMember(long boardId, String login) {
        if (members.existsByBoardIdAndUsername(boardId, login)) {
            return false;
        }
        members.save(new BoardMember(boardId, login, clock.instant()));
        return true;
    }

    private boolean canAccess(Board board, String login) {
        return !board.isMembersOnly() || (login != null && members.existsByBoardIdAndUsername(board.getId(), key(login)));
    }

    private static String key(String login) {
        return login == null ? "" : login.strip().toLowerCase(Locale.ROOT);
    }

    private Board requireBoard(long boardId) {
        return boards.findById(boardId).orElseThrow(() -> new NotFoundException("Board", boardId));
    }
}
