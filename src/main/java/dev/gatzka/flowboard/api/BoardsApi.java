package dev.gatzka.flowboard.api;

import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.BoardSummary;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.ActivityEntry;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.CardTemplateDraft;
import dev.gatzka.flowboard.domain.CardTemplateSnapshot;
import dev.gatzka.flowboard.domain.WeeklyDigest;
import dev.gatzka.flowboard.exchange.BoardExchangeService;
import dev.gatzka.flowboard.exchange.BoardExport;
import dev.gatzka.flowboard.security.AccountService;
import dev.gatzka.flowboard.service.AccessService;
import dev.gatzka.flowboard.service.ActivityLog;
import dev.gatzka.flowboard.service.BoardService;
import dev.gatzka.flowboard.service.CardTemplateService;
import dev.gatzka.flowboard.service.DigestService;
import dev.gatzka.flowboard.service.TimeService;
import dev.gatzka.flowboard.service.NotFoundException;
import java.net.URI;
import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final DigestService digests;
    private final TimeService time;
    private final CardTemplateService templates;
    private final ActivityLog activityLog;
    private final AccessService access;
    private final Clock clock;

    BoardsApi(BoardService boardService, BoardExchangeService exchange, AccountService accounts, DigestService digests,
            TimeService time, CardTemplateService templates, ActivityLog activityLog, AccessService access, Clock clock) {
        this.access = access;
        this.boardService = boardService;
        this.exchange = exchange;
        this.accounts = accounts;
        this.digests = digests;
        this.time = time;
        this.templates = templates;
        this.activityLog = activityLog;
        this.clock = clock;
    }

    /** The name recorded in the activity feed: the display name of the caller's account. */
    private String actor(Principal caller) {
        return accounts.displayNameOf(caller.getName());
    }

    @GetMapping("/boards")
    public List<BoardSummary> boards(Principal caller) {
        return access.visibleBoards(caller.getName());
    }

    @GetMapping("/boards/{boardId}")
    public BoardExport board(@PathVariable long boardId, Principal caller) {
        requireAccess(boardId, caller);
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
        requireAccess(boardId, caller);
        boardService.deleteBoard(boardId, actor(caller));
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/boards/{boardId}/lanes/{laneId}/cards", consumes = "application/json")
    public ResponseEntity<CardSnapshot> createCard(@PathVariable long boardId, @PathVariable long laneId,
            @RequestBody CardDraft draft, Principal caller) {
        requireAccess(boardId, caller);
        requireLaneOnBoard(boardId, laneId);
        CardSnapshot created = boardService.createCard(laneId, draft, actor(caller));
        return ResponseEntity.created(URI.create("/api/cards/" + created.id())).body(created);
    }

    @PutMapping(path = "/cards/{cardId}", consumes = "application/json")
    public CardSnapshot updateCard(@PathVariable long cardId, @RequestBody CardDraft draft, Principal caller) {
        requireCardAccess(cardId, caller);
        return boardService.updateCard(cardId, draft, actor(caller));
    }

    /** Moves a card to {@code targetIndex} in {@code targetLaneId}; the index is clamped to the lane. */
    @PostMapping(path = "/cards/{cardId}/move", consumes = "application/json")
    public ResponseEntity<Void> moveCard(@PathVariable long cardId, @RequestBody MoveRequest move, Principal caller) {
        requireCardAccess(cardId, caller);
        boardService.moveCard(cardId, move.targetLaneId(), move.targetIndex(), actor(caller));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/cards/{cardId}")
    public ResponseEntity<Void> deleteCard(@PathVariable long cardId, Principal caller) {
        requireCardAccess(cardId, caller);
        boardService.deleteCard(cardId, actor(caller));
        return ResponseEntity.noContent().build();
    }

    /** The board's recent history, newest first. */
    @GetMapping("/boards/{boardId}/activity")
    public List<ActivityEntry> activity(@PathVariable long boardId, Principal caller) {
        requireAccess(boardId, caller);
        return activityLog.recent(boardId);
    }

    /** The weekly digest as data; {@code week} is the last day of the week, default today. */
    @GetMapping("/boards/{boardId}/digest")
    public WeeklyDigest digest(@PathVariable long boardId, @RequestParam(required = false) LocalDate week, Principal caller) {
        requireAccess(boardId, caller);
        return digests.weekly(boardId, week == null ? LocalDate.now(clock) : week);
    }

    /** The same digest as Markdown, ready to paste into a chat. */
    @GetMapping(path = "/boards/{boardId}/digest.md", produces = "text/markdown;charset=UTF-8")
    public String digestMarkdown(@PathVariable long boardId, @RequestParam(required = false) LocalDate week, Principal caller) {
        return digest(boardId, week, caller).toMarkdown();
    }

    /** Logs time on a card in the caller's name. */
    @PostMapping(path = "/cards/{cardId}/time", consumes = "application/json")
    public ResponseEntity<CardSnapshot> logTime(@PathVariable long cardId, @RequestBody TimeRequest request, Principal caller) {
        requireCardAccess(cardId, caller);
        String actor = actor(caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(time.log(cardId, actor, request.minutes(), request.note(), actor));
    }

    @GetMapping("/boards/{boardId}/templates")
    public List<CardTemplateSnapshot> templates(@PathVariable long boardId, Principal caller) {
        requireAccess(boardId, caller);
        return templates.forBoard(boardId);
    }

    /** Creates a template or replaces the one with the same name. */
    @PostMapping(path = "/boards/{boardId}/templates", consumes = "application/json")
    public ResponseEntity<CardTemplateSnapshot> saveTemplate(@PathVariable long boardId, @RequestBody CardTemplateDraft draft, Principal caller) {
        requireAccess(boardId, caller);
        CardTemplateSnapshot saved = templates.save(boardId, draft, actor(caller));
        return ResponseEntity.created(URI.create("/api/templates/" + saved.id())).body(saved);
    }

    @DeleteMapping("/templates/{templateId}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable long templateId, Principal caller) {
        templates.delete(templateId, actor(caller));
        return ResponseEntity.noContent().build();
    }

    /** Creates a card from a template at the end of the given lane. */
    @PostMapping(path = "/templates/{templateId}/cards", consumes = "application/json")
    public ResponseEntity<CardSnapshot> createFromTemplate(@PathVariable long templateId, @RequestBody FromTemplateRequest request,
            Principal caller) {
        CardSnapshot created = templates.instantiate(templateId, request.laneId(), actor(caller));
        return ResponseEntity.created(URI.create("/api/cards/" + created.id())).body(created);
    }

    public record MoveRequest(long targetLaneId, int targetIndex) {
    }

    public record TimeRequest(int minutes, String note) {
    }

    public record FromTemplateRequest(long laneId) {
    }

    /** Boards the caller may not see are reported as missing, so their existence is not leaked. */
    private void requireAccess(long boardId, Principal caller) {
        if (!access.canAccess(boardId, caller.getName())) {
            throw new NotFoundException("Board", boardId);
        }
    }

    private void requireCardAccess(long cardId, Principal caller) {
        if (!access.canAccessCard(cardId, caller.getName())) {
            throw new NotFoundException("Card", cardId);
        }
    }

    private void requireLaneOnBoard(long boardId, long laneId) {
        boardService.findBoard(boardId)
                .flatMap(board -> board.lane(laneId))
                .orElseThrow(() -> new NotFoundException("Lane", laneId));
    }
}
