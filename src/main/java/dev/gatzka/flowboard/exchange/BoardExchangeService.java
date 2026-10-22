package dev.gatzka.flowboard.exchange;

import dev.gatzka.flowboard.domain.Board;
import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.Card;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardFilter;
import dev.gatzka.flowboard.domain.CardFilterCodec;
import dev.gatzka.flowboard.domain.CardTemplate;
import dev.gatzka.flowboard.domain.CardTemplateDraft;
import dev.gatzka.flowboard.domain.LaneRule;
import dev.gatzka.flowboard.domain.SavedView;
import dev.gatzka.flowboard.domain.ChecklistItem;
import dev.gatzka.flowboard.domain.Comment;
import dev.gatzka.flowboard.domain.Lane;
import dev.gatzka.flowboard.domain.LaneDraft;
import dev.gatzka.flowboard.domain.Milestone;
import dev.gatzka.flowboard.domain.MilestoneDraft;
import dev.gatzka.flowboard.domain.MilestoneSnapshot;
import dev.gatzka.flowboard.exchange.BoardExport.CardExport;
import dev.gatzka.flowboard.exchange.BoardExport.MilestoneExport;
import dev.gatzka.flowboard.exchange.BoardExport.RuleExport;
import dev.gatzka.flowboard.exchange.BoardExport.TemplateExport;
import dev.gatzka.flowboard.exchange.BoardExport.ViewExport;
import dev.gatzka.flowboard.exchange.BoardExport.ChecklistItemExport;
import dev.gatzka.flowboard.exchange.BoardExport.CommentExport;
import dev.gatzka.flowboard.exchange.BoardExport.LaneExport;
import dev.gatzka.flowboard.repository.BoardRepository;
import dev.gatzka.flowboard.repository.CardRepository;
import dev.gatzka.flowboard.repository.CardTemplateRepository;
import dev.gatzka.flowboard.repository.LaneRuleRepository;
import dev.gatzka.flowboard.repository.MilestoneRepository;
import dev.gatzka.flowboard.repository.SavedViewRepository;
import dev.gatzka.flowboard.repository.LaneRepository;
import dev.gatzka.flowboard.service.ActivityLog;
import dev.gatzka.flowboard.service.AutomationService;
import dev.gatzka.flowboard.service.CardTemplateService;
import dev.gatzka.flowboard.service.SavedViewService;
import dev.gatzka.flowboard.service.BoardChangedEvent;
import dev.gatzka.flowboard.service.BoardService;
import dev.gatzka.flowboard.service.MilestoneService;
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
import tools.jackson.databind.JsonNode;
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
    private final CardTemplateService templates;
    private final SavedViewService views;
    private final AutomationService automations;
    private final CardTemplateRepository templateRepository;
    private final SavedViewRepository viewRepository;
    private final LaneRuleRepository ruleRepository;
    private final MilestoneRepository milestoneRepository;
    private final MilestoneService milestones;
    private final Clock clock;

    BoardExchangeService(BoardRepository boards, LaneRepository lanes, CardRepository cards, BoardService boardService,
            ActivityLog activityLog, JsonMapper json, CardTemplateService templates, SavedViewService views,
            AutomationService automations, CardTemplateRepository templateRepository, SavedViewRepository viewRepository,
            LaneRuleRepository ruleRepository, MilestoneRepository milestoneRepository, MilestoneService milestones, Clock clock) {
        this.milestoneRepository = milestoneRepository;
        this.milestones = milestones;
        this.templates = templates;
        this.views = views;
        this.automations = automations;
        this.templateRepository = templateRepository;
        this.viewRepository = viewRepository;
        this.ruleRepository = ruleRepository;
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
        List<MilestoneSnapshot> boardMilestones = milestones.forBoard(boardId);
        Map<Long, String> milestoneNames = boardMilestones.stream().collect(Collectors.toMap(MilestoneSnapshot::id, MilestoneSnapshot::name));
        List<LaneExport> laneExports = lanes.findAllByBoardIdOrderByPositionAsc(boardId).stream()
                .map(lane -> new LaneExport(lane.getName(), lane.getWipLimit(), lane.isDone(),
                        cardsByLane.getOrDefault(lane.getId(), List.of()).stream().map(card -> export(card, milestoneNames)).toList(),
                        automations.rulesFor(lane.getId()).stream().map(rule -> new RuleExport(rule.action(), rule.argument())).toList()))
                .toList();
        List<TemplateExport> templateExports = templates.forBoard(boardId).stream().map(template -> {
            CardTemplateDraft draft = template.draft();
            return new TemplateExport(draft.name(), draft.title(), draft.description(), draft.priority(), draft.assignee(),
                    draft.tags(), draft.estimate(), draft.checklist());
        }).toList();
        List<ViewExport> viewExports = views.forBoard(boardId).stream()
                .map(view -> new ViewExport(view.name(), CardFilterCodec.encode(view.filter()))).toList();
        List<MilestoneExport> milestoneExports = boardMilestones.stream()
                .map(milestone -> new MilestoneExport(milestone.name(), milestone.targetDate())).toList();
        return new BoardExport(BoardExport.CURRENT_VERSION, board.getName(), board.getDescription(), clock.instant(), laneExports,
                templateExports, viewExports, milestoneExports);
    }

    public byte[] toJson(BoardExport export) {
        return json.writerWithDefaultPrettyPrinter().writeValueAsString(export).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Reads a Flowboard export, or a Trello board export which is converted on the fly.
     *
     * @throws InvalidBoardFileException when the bytes are not a board file this version understands
     */
    public BoardExport parse(byte[] bytes) {
        try {
            JsonNode root = json.readTree(bytes);
            if (TrelloImport.looksLikeTrello(root)) {
                return validate(TrelloImport.convert(root));
            }
            return validate(json.treeToValue(root, BoardExport.class));
        } catch (JacksonException malformed) {
            throw new InvalidBoardFileException("The file is not valid JSON", malformed);
        }
    }

    /** Converts a Trello board export into the Flowboard shape without importing it. */
    public BoardExport fromTrello(JsonNode trello) {
        if (!TrelloImport.looksLikeTrello(trello)) {
            throw new InvalidBoardFileException("This is not a Trello board export");
        }
        return validate(TrelloImport.convert(trello));
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
        return new BoardExport(BoardExport.CURRENT_VERSION, export.name(), export.description(), export.exportedAt(), export.lanes(),
                export.templates(), export.views(), export.milestones());
    }

    /** Creates a new board from an export, preserving lane order, card order, checklists and comments. */
    public BoardSnapshot importBoard(BoardExport export, String actor) {
        Instant now = clock.instant();
        Board board = boards.save(new Board(new BoardDraft(export.name(), export.description()), now));
        Map<String, Long> milestoneIds = new java.util.HashMap<>();
        for (MilestoneExport milestone : export.milestones()) {
            try {
                milestoneIds.put(milestone.name().strip(), milestoneRepository.save(new Milestone(board.getId(),
                        new MilestoneDraft(milestone.name(), milestone.targetDate()), now)).getId());
            } catch (IllegalArgumentException | NullPointerException invalid) {
                throw new InvalidBoardFileException("A milestone needs a name");
            }
        }
        int lanePosition = 0;
        for (LaneExport laneExport : export.lanes()) {
            Lane lane = lanes.save(new Lane(board, new LaneDraft(laneExport.name(), laneExport.wipLimit(), laneExport.done()), lanePosition++));
            int cardPosition = 0;
            for (CardExport cardExport : laneExport.cards()) {
                Card card = new Card(lane, draft(cardExport), cardPosition++, now);
                card.setRecurrence(cardExport.recurrence(), now);
                if (cardExport.milestone() != null) {
                    Long milestoneId = milestoneIds.get(cardExport.milestone().strip());
                    if (milestoneId == null) {
                        throw new InvalidBoardFileException("The card \"" + card.getTitle() + "\" refers to an unknown milestone");
                    }
                    card.setMilestone(milestoneId, now);
                }
                for (ChecklistItemExport item : cardExport.checklist()) {
                    ChecklistItem added = card.addChecklistItem(item.text(), now);
                    added.setDone(item.done());
                }
                for (CommentExport comment : cardExport.comments()) {
                    card.addComment(comment.author(), comment.text(), Objects.requireNonNullElse(comment.createdAt(), now));
                }
                cards.save(card);
            }
            for (RuleExport rule : laneExport.rules()) {
                if (rule.action() == null) {
                    throw new InvalidBoardFileException("A lane rule has no action");
                }
                try {
                    ruleRepository.save(new LaneRule(lane.getId(), rule.action(), rule.action().normalise(rule.argument()), now));
                } catch (IllegalArgumentException invalid) {
                    throw new InvalidBoardFileException("Lane rule on " + lane.getName() + ": " + invalid.getMessage());
                }
            }
        }
        for (TemplateExport template : export.templates()) {
            try {
                templateRepository.save(new CardTemplate(board.getId(), new CardTemplateDraft(template.name(), template.title(),
                        template.description(), template.priority(), template.assignee(), template.tags(), template.estimate(),
                        template.checklist()), now));
            } catch (IllegalArgumentException invalid) {
                throw new InvalidBoardFileException("Card template: " + invalid.getMessage());
            }
        }
        for (ViewExport view : export.views()) {
            CardFilter filter = CardFilterCodec.decode(view.filter());
            if (view.name() == null || view.name().isBlank() || !filter.isActive()) {
                throw new InvalidBoardFileException("A saved view needs a name and a filter");
            }
            viewRepository.save(new SavedView(board.getId(), view.name().strip(), CardFilterCodec.encode(filter), actor, now));
        }
        activityLog.record(BoardChangedEvent.created(board.getId(), actor,
                "imported board \"%s\" with %d cards".formatted(board.getName(), export.cardCount())));
        return boardService.findBoard(board.getId()).orElseThrow();
    }

    private static CardExport export(Card card, Map<Long, String> milestoneNames) {
        return new CardExport(
                card.getTitle(),
                card.getDescription(),
                card.getPriority(),
                card.getStartDate(),
                card.getDueDate(),
                card.getAssignee(),
                card.getTags().stream().collect(Collectors.toCollection(java.util.TreeSet::new)),
                card.getEstimate(),
                card.getRecurrence(),
                card.getChecklist().stream().map(item -> new ChecklistItemExport(item.getText(), item.isDone())).toList(),
                card.getComments().stream().map(comment -> new CommentExport(comment.getAuthor(), comment.getText(), comment.getCreatedAt())).toList(),
                card.getMilestoneId() == null ? null : milestoneNames.get(card.getMilestoneId()));
    }

    private static CardDraft draft(CardExport card) {
        return new CardDraft(card.title(), card.description(), card.priority(), card.startDate(), card.dueDate(), card.assignee(),
                card.tags(), card.estimate());
    }
}
