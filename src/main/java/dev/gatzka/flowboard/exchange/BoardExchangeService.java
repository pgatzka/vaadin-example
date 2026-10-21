package dev.gatzka.flowboard.exchange;

import dev.gatzka.flowboard.domain.Board;
import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.Card;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.ChecklistItem;
import dev.gatzka.flowboard.domain.Comment;
import dev.gatzka.flowboard.domain.Lane;
import dev.gatzka.flowboard.domain.LaneDraft;
import dev.gatzka.flowboard.exchange.BoardExport.CardExport;
import dev.gatzka.flowboard.exchange.BoardExport.ChecklistItemExport;
import dev.gatzka.flowboard.exchange.BoardExport.CommentExport;
import dev.gatzka.flowboard.exchange.BoardExport.LaneExport;
import dev.gatzka.flowboard.repository.BoardRepository;
import dev.gatzka.flowboard.repository.CardRepository;
import dev.gatzka.flowboard.repository.LaneRepository;
import dev.gatzka.flowboard.service.ActivityLog;
import dev.gatzka.flowboard.service.BoardChangedEvent;
import dev.gatzka.flowboard.service.BoardService;
import dev.gatzka.flowboard.service.NotFoundException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Turns boards into portable JSON and back. */
@Service
@Transactional
public class BoardExchangeService {

    private final BoardRepository boards;
    private final LaneRepository lanes;
    private final CardRepository cards;
    private final BoardService boardService;
    private final ActivityLog activityLog;
    private final JsonMapper json;
    private final Clock clock;

    BoardExchangeService(BoardRepository boards, LaneRepository lanes, CardRepository cards, BoardService boardService,
            ActivityLog activityLog, JsonMapper json, Clock clock) {
        this.boards = boards;
        this.lanes = lanes;
        this.cards = cards;
        this.boardService = boardService;
        this.activityLog = activityLog;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public BoardExport export(long boardId) {
        Board board = boards.findById(boardId).orElseThrow(() -> new NotFoundException("Board", boardId));
        Map<Long, List<Card>> cardsByLane = cards.findAllByLaneBoardIdAndArchivedAtIsNullOrderByPositionAsc(boardId).stream()
                .collect(Collectors.groupingBy(card -> card.getLane().getId()));
        List<LaneExport> laneExports = lanes.findAllByBoardIdOrderByPositionAsc(boardId).stream()
                .map(lane -> new LaneExport(lane.getName(), lane.getWipLimit(), lane.isDone(),
                        cardsByLane.getOrDefault(lane.getId(), List.of()).stream().map(BoardExchangeService::export).toList()))
                .toList();
        return new BoardExport(BoardExport.CURRENT_VERSION, board.getName(), board.getDescription(), clock.instant(), laneExports);
    }

    public byte[] toJson(BoardExport export) {
        return json.writerWithDefaultPrettyPrinter().writeValueAsString(export).getBytes(StandardCharsets.UTF_8);
    }

    /** @throws InvalidBoardFileException when the bytes are not a board file this version understands */
    public BoardExport parse(byte[] bytes) {
        try {
            return validate(json.readValue(bytes, BoardExport.class));
        } catch (JacksonException malformed) {
            throw new InvalidBoardFileException("The file is not valid JSON", malformed);
        }
    }

    /**
     * Checks that an export describes a board this version can import. A missing version or lane
     * list is tolerated so that hand-written API payloads stay short.
     */
    public BoardExport validate(BoardExport export) {
        if (export == null || export.name() == null || export.name().isBlank()) {
            throw new InvalidBoardFileException("The file does not describe a board");
        }
        if (export.version() != 0 && export.version() != BoardExport.CURRENT_VERSION) {
            throw new InvalidBoardFileException("Unsupported board file version " + export.version());
        }
        return new BoardExport(BoardExport.CURRENT_VERSION, export.name(), export.description(), export.exportedAt(), export.lanes());
    }

    /** Creates a new board from an export, preserving lane order, card order, checklists and comments. */
    public BoardSnapshot importBoard(BoardExport export, String actor) {
        Instant now = clock.instant();
        Board board = boards.save(new Board(new BoardDraft(export.name(), export.description()), now));
        int lanePosition = 0;
        for (LaneExport laneExport : export.lanes()) {
            Lane lane = lanes.save(new Lane(board, new LaneDraft(laneExport.name(), laneExport.wipLimit(), laneExport.done()), lanePosition++));
            int cardPosition = 0;
            for (CardExport cardExport : laneExport.cards()) {
                Card card = new Card(lane, draft(cardExport), cardPosition++, now);
                for (ChecklistItemExport item : cardExport.checklist()) {
                    ChecklistItem added = card.addChecklistItem(item.text(), now);
                    added.setDone(item.done());
                }
                for (CommentExport comment : cardExport.comments()) {
                    card.addComment(comment.author(), comment.text(), Objects.requireNonNullElse(comment.createdAt(), now));
                }
                cards.save(card);
            }
        }
        activityLog.record(BoardChangedEvent.created(board.getId(), actor,
                "imported board \"%s\" with %d cards".formatted(board.getName(), export.cardCount())));
        return boardService.findBoard(board.getId()).orElseThrow();
    }

    private static CardExport export(Card card) {
        return new CardExport(
                card.getTitle(),
                card.getDescription(),
                card.getPriority(),
                card.getDueDate(),
                card.getAssignee(),
                card.getTags().stream().collect(Collectors.toCollection(java.util.TreeSet::new)),
                card.getEstimate(),
                card.getChecklist().stream().map(item -> new ChecklistItemExport(item.getText(), item.isDone())).toList(),
                card.getComments().stream().map(comment -> new CommentExport(comment.getAuthor(), comment.getText(), comment.getCreatedAt())).toList());
    }

    private static CardDraft draft(CardExport card) {
        return new CardDraft(card.title(), card.description(), card.priority(), card.dueDate(), card.assignee(), card.tags(), card.estimate());
    }
}
