package dev.gatzka.flowboard.api;

import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.BoardSummary;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.exchange.BoardExchangeService;
import dev.gatzka.flowboard.exchange.BoardExport;
import dev.gatzka.flowboard.security.AccountService;
import dev.gatzka.flowboard.service.BoardService;
import dev.gatzka.flowboard.service.NotFoundException;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * JSON API for scripts and integrations. Boards use the same portable shape as the JSON export,
 * so a file exported from the UI can be posted back here and vice versa. Changes made through the
 * API show up live in every open browser like any other change.
 */
@RestController
@RequestMapping(path = "/api", produces = "application/json")
public class BoardsApi {

    private final BoardService boardService;
    private final BoardExchangeService exchange;
    private final AccountService accounts;

    BoardsApi(BoardService boardService, BoardExchangeService exchange, AccountService accounts) {
        this.boardService = boardService;
        this.exchange = exchange;
        this.accounts = accounts;
    }

    /** The name recorded in the activity feed: the display name of the caller's account. */
    private String actor(Principal caller) {
        return accounts.displayNameOf(caller.getName());
    }

    @GetMapping("/boards")
    public List<BoardSummary> boards() {
        return boardService.listBoards();
    }

    @GetMapping("/boards/{boardId}")
    public BoardExport board(@PathVariable long boardId) {
        return exchange.export(boardId);
    }

    /** Creates a board from the export format; lanes and cards are optional. */
    @PostMapping(path = "/boards", consumes = "application/json")
    public ResponseEntity<BoardSnapshot> createBoard(@RequestBody BoardExport export, Principal caller) {
        BoardSnapshot created = exchange.importBoard(exchange.validate(export), actor(caller));
        return ResponseEntity.created(URI.create("/api/boards/" + created.id())).body(created);
    }

    @DeleteMapping("/boards/{boardId}")
    public ResponseEntity<Void> deleteBoard(@PathVariable long boardId, Principal caller) {
        boardService.deleteBoard(boardId, actor(caller));
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/boards/{boardId}/lanes/{laneId}/cards", consumes = "application/json")
    public ResponseEntity<CardSnapshot> createCard(@PathVariable long boardId, @PathVariable long laneId,
            @RequestBody CardDraft draft, Principal caller) {
        requireLaneOnBoard(boardId, laneId);
        CardSnapshot created = boardService.createCard(laneId, draft, actor(caller));
        return ResponseEntity.created(URI.create("/api/cards/" + created.id())).body(created);
    }

    @PutMapping(path = "/cards/{cardId}", consumes = "application/json")
    public CardSnapshot updateCard(@PathVariable long cardId, @RequestBody CardDraft draft, Principal caller) {
        return boardService.updateCard(cardId, draft, actor(caller));
    }

    /** Moves a card to {@code targetIndex} in {@code targetLaneId}; the index is clamped to the lane. */
    @PostMapping(path = "/cards/{cardId}/move", consumes = "application/json")
    public ResponseEntity<Void> moveCard(@PathVariable long cardId, @RequestBody MoveRequest move, Principal caller) {
        boardService.moveCard(cardId, move.targetLaneId(), move.targetIndex(), actor(caller));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/cards/{cardId}")
    public ResponseEntity<Void> deleteCard(@PathVariable long cardId, Principal caller) {
        boardService.deleteCard(cardId, actor(caller));
        return ResponseEntity.noContent().build();
    }

    public record MoveRequest(long targetLaneId, int targetIndex) {
    }

    private void requireLaneOnBoard(long boardId, long laneId) {
        boardService.findBoard(boardId)
                .flatMap(board -> board.lane(laneId))
                .orElseThrow(() -> new NotFoundException("Lane", laneId));
    }
}
