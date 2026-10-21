package dev.gatzka.flowboard.service;

import static java.util.stream.Collectors.groupingBy;

import dev.gatzka.flowboard.domain.Board;
import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.BoardSummary;
import dev.gatzka.flowboard.domain.BoardTemplate;
import dev.gatzka.flowboard.domain.Card;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.Lane;
import dev.gatzka.flowboard.domain.LaneDraft;
import dev.gatzka.flowboard.domain.LaneSnapshot;
import dev.gatzka.flowboard.repository.BoardRepository;
import dev.gatzka.flowboard.repository.CardRepository;
import dev.gatzka.flowboard.repository.LaneRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * All use cases around boards, lanes and cards.
 * <p>
 * The service hands out immutable snapshots rather than entities, so the UI never touches a
 * persistence context. Every mutation records an activity entry, which in turn notifies all
 * connected browsers.
 */
@Service
@Transactional
public class BoardService {

    private final BoardRepository boards;
    private final LaneRepository lanes;
    private final CardRepository cards;
    private final ActivityLog activityLog;
    private final NotificationService notifications;
    private final VisitService visits;
    private final Clock clock;

    BoardService(BoardRepository boards, LaneRepository lanes, CardRepository cards, ActivityLog activityLog,
            NotificationService notifications, VisitService visits, Clock clock) {
        this.visits = visits;
        this.boards = boards;
        this.lanes = lanes;
        this.cards = cards;
        this.activityLog = activityLog;
        this.notifications = notifications;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- boards

    @Transactional(readOnly = true)
    public List<BoardSummary> listBoards() {
        LocalDate today = LocalDate.now(clock);
        return boards.findAll(Sort.by("createdAt")).stream()
                .map(board -> summarise(board, today))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<BoardSnapshot> findBoard(long boardId) {
        return boards.findById(boardId).map(this::snapshot);
    }

    /** Creates a board with the standard Kanban lanes. */
    public BoardSnapshot createBoard(BoardDraft draft, String actor) {
        return createBoard(draft, BoardTemplate.KANBAN, actor);
    }

    public BoardSnapshot createBoard(BoardDraft draft, BoardTemplate template, String actor) {
        Board board = boards.save(new Board(draft, clock.instant()));
        List<LaneDraft> templateLanes = template.lanes();
        for (int i = 0; i < templateLanes.size(); i++) {
            lanes.save(new Lane(board, templateLanes.get(i), i));
        }
        activityLog.record(BoardChangedEvent.created(board.getId(), actor,
                "created board \"%s\" from the %s template".formatted(board.getName(), template.label())));
        return snapshot(board);
    }

    public void updateBoard(long boardId, BoardDraft draft, String actor) {
        Board board = requireBoard(boardId);
        board.update(draft);
        activityLog.record(BoardChangedEvent.updated(boardId, actor, "updated the board details"));
    }

    public void deleteBoard(long boardId, String actor) {
        Board board = requireBoard(boardId);
        cards.clearBlockersOfBoard(boardId);
        cards.deleteAllByLaneBoardId(boardId);
        lanes.deleteAllByBoardId(boardId);
        activityLog.forget(boardId);
        notifications.forgetBoard(boardId);
        visits.forgetBoard(boardId);
        boards.delete(board);
        activityLog.record(BoardChangedEvent.deleted(boardId, actor, "deleted board \"%s\"".formatted(board.getName())));
    }

    // ----------------------------------------------------------------- lanes

    public LaneSnapshot addLane(long boardId, LaneDraft draft, String actor) {
        Board board = requireBoard(boardId);
        int position = (int) lanes.countByBoardId(boardId);
        Lane lane = lanes.save(new Lane(board, draft, position));
        activityLog.record(BoardChangedEvent.content(boardId, actor, "added lane \"%s\"".formatted(lane.getName())));
        return LaneSnapshot.of(lane, List.of());
    }

    public void updateLane(long laneId, LaneDraft draft, String actor) {
        Lane lane = requireLane(laneId);
        boolean wasDone = lane.isDone();
        lane.update(draft);
        if (wasDone != lane.isDone()) {
            Instant now = clock.instant();
            cards.findAllByLaneIdAndArchivedAtIsNullOrderByPositionAsc(laneId).forEach(card -> card.markCompletion(lane.isDone(), now));
        }
        activityLog.record(BoardChangedEvent.content(boardIdOf(lane), actor, "updated lane \"%s\"".formatted(lane.getName())));
    }

    /** Deletes a lane together with all of its cards. */
    public void deleteLane(long laneId, String actor) {
        Lane lane = requireLane(laneId);
        long boardId = boardIdOf(lane);
        unlinkBlockersInLane(laneId);
        cards.deleteAllByLaneId(laneId);
        removeLane(lane);
        activityLog.record(BoardChangedEvent.content(boardId, actor, "removed lane \"%s\"".formatted(lane.getName())));
    }

    /** Deletes a lane after appending its cards, in order, to another lane of the same board. */
    public void deleteLaneMovingCards(long laneId, long targetLaneId, String actor) {
        Lane lane = requireLane(laneId);
        Lane target = requireLane(targetLaneId);
        if (lane.getId().equals(target.getId())) {
            throw new IllegalArgumentException("Cards cannot be moved into the lane being deleted");
        }
        if (boardIdOf(lane) != boardIdOf(target)) {
            throw new IllegalArgumentException("Cards can only be moved within their own board");
        }
        List<Card> targetCards = cards.findAllByLaneIdAndArchivedAtIsNullOrderByPositionAsc(target.getId());
        List<Card> moving = cards.findAllByLaneIdAndArchivedAtIsNullOrderByPositionAsc(laneId);
        Instant now = clock.instant();
        for (Card card : moving) {
            card.moveTo(target, now);
            targetCards.add(card);
        }
        renumberCards(targetCards);
        removeLane(lane);
        activityLog.record(BoardChangedEvent.content(boardIdOf(lane), actor,
                "removed lane \"%s\" and moved %s to %s".formatted(lane.getName(), count(moving.size(), "card"), target.getName())));
    }

    public void moveLane(long laneId, int targetIndex, String actor) {
        Lane lane = requireLane(laneId);
        long boardId = boardIdOf(lane);
        List<Lane> ordered = lanes.findAllByBoardIdOrderByPositionAsc(boardId);
        int currentIndex = ordered.indexOf(lane);
        int newIndex = clamp(targetIndex, ordered.size() - 1);
        if (newIndex == currentIndex) {
            return;
        }
        ordered.remove(currentIndex);
        ordered.add(newIndex, lane);
        renumberLanes(ordered);
        activityLog.record(BoardChangedEvent.content(boardId, actor, "moved lane \"%s\"".formatted(lane.getName())));
    }

    // ----------------------------------------------------------------- cards

    public CardSnapshot createCard(long laneId, CardDraft draft, String actor) {
        Lane lane = requireLane(laneId);
        int position = (int) cards.countByLaneIdAndArchivedAtIsNull(laneId);
        Card card = cards.save(new Card(lane, draft, position, clock.instant()));
        activityLog.record(BoardChangedEvent.content(boardIdOf(lane), actor,
                "added \"%s\" to %s".formatted(card.getTitle(), lane.getName())).forCard(card.getId()));
        notifyAssignment(card, null, actor);
        return CardSnapshot.of(card);
    }

    public CardSnapshot updateCard(long cardId, CardDraft draft, String actor) {
        Card card = requireCard(cardId);
        String previousAssignee = card.getAssignee();
        card.update(draft, clock.instant());
        notifyAssignment(card, previousAssignee, actor);
        activityLog.record(BoardChangedEvent.content(boardIdOf(card.getLane()), actor,
                "updated \"%s\"".formatted(card.getTitle())).forCard(cardId));
        return CardSnapshot.of(card);
    }

    public void deleteCard(long cardId, String actor) {
        Card card = requireCard(cardId);
        Lane lane = card.getLane();
        Instant now = clock.instant();
        cards.findAllBlockedBy(cardId).forEach(blocked -> blocked.removeBlocker(card, now));
        cards.delete(card);
        renumberCards(cards.findAllByLaneIdAndArchivedAtIsNullOrderByPositionAsc(lane.getId()));
        activityLog.record(BoardChangedEvent.content(boardIdOf(lane), actor,
                "deleted \"%s\"".formatted(card.getTitle())).forCard(cardId));
    }

    /** Reorders a lane's live cards by votes, most first; ties keep their current order. */
    public void sortLaneByVotes(long laneId, String actor) {
        Lane lane = requireLane(laneId);
        List<Card> ordered = new java.util.ArrayList<>(cards.findAllByLaneIdAndArchivedAtIsNullOrderByPositionAsc(laneId));
        ordered.sort(java.util.Comparator.comparingInt((Card card) -> card.getVoters().size()).reversed());
        renumberCards(ordered);
        activityLog.record(BoardChangedEvent.content(boardIdOf(lane), actor, "sorted %s by votes".formatted(lane.getName())));
    }

    /** Takes a card off the board without deleting it. */
    public void archiveCard(long cardId, String actor) {
        Card card = requireCard(cardId);
        if (card.isArchived()) {
            return;
        }
        Lane lane = card.getLane();
        card.archive(clock.instant());
        renumberCards(cards.findAllByLaneIdAndArchivedAtIsNullOrderByPositionAsc(lane.getId()));
        activityLog.record(BoardChangedEvent.content(boardIdOf(lane), actor,
                "archived \"%s\"".formatted(card.getTitle())).forCard(cardId));
    }

    /** Archives every live card in the lane; returns how many were archived. */
    public int archiveLane(long laneId, String actor) {
        Lane lane = requireLane(laneId);
        List<Card> live = cards.findAllByLaneIdAndArchivedAtIsNullOrderByPositionAsc(laneId);
        if (live.isEmpty()) {
            return 0;
        }
        Instant now = clock.instant();
        live.forEach(card -> card.archive(now));
        activityLog.record(BoardChangedEvent.content(boardIdOf(lane), actor,
                "archived %s from %s".formatted(count(live.size(), "card"), lane.getName())));
        return live.size();
    }

    /** Puts an archived card back at the end of its lane. */
    public void restoreCard(long cardId, String actor) {
        Card card = requireCard(cardId);
        if (!card.isArchived()) {
            return;
        }
        Lane lane = card.getLane();
        card.restore((int) cards.countByLaneIdAndArchivedAtIsNull(lane.getId()), clock.instant());
        activityLog.record(BoardChangedEvent.content(boardIdOf(lane), actor,
                "restored \"%s\" to %s".formatted(card.getTitle(), lane.getName())).forCard(cardId));
    }

    @Transactional(readOnly = true)
    public List<CardSnapshot> archivedCards(long boardId) {
        return cards.findAllByLaneBoardIdAndArchivedAtIsNotNullOrderByArchivedAtDesc(boardId).stream().map(CardSnapshot::of).toList();
    }

    /**
     * Moves a card to {@code targetIndex} within {@code targetLaneId}, which may be the card's
     * current lane. The index refers to the target lane as it looks before the move, so dropping
     * a card "onto" another card means passing that card's position.
     */
    public void moveCard(long cardId, long targetLaneId, int targetIndex, String actor) {
        Card card = requireCard(cardId);
        if (card.isArchived()) {
            throw new IllegalArgumentException("Archived cards cannot be moved; restore the card first");
        }
        Lane source = card.getLane();
        Lane target = requireLane(targetLaneId);
        if (boardIdOf(source) != boardIdOf(target)) {
            throw new IllegalArgumentException("Cards can only be moved within their own board");
        }

        List<Card> sourceCards = cards.findAllByLaneIdAndArchivedAtIsNullOrderByPositionAsc(source.getId());
        boolean sameLane = source.getId().equals(target.getId());
        List<Card> targetCards = sameLane ? sourceCards : cards.findAllByLaneIdAndArchivedAtIsNullOrderByPositionAsc(target.getId());

        int currentIndex = sourceCards.indexOf(card);
        sourceCards.remove(currentIndex);
        int newIndex = targetIndex;
        if (sameLane && currentIndex < targetIndex) {
            newIndex--; // the gap left by the card shifts everything below it up by one
        }
        newIndex = clamp(newIndex, targetCards.size());
        if (sameLane && newIndex == currentIndex) {
            return;
        }
        targetCards.add(newIndex, card);
        card.moveTo(target, clock.instant());
        renumberCards(sourceCards);
        renumberCards(targetCards);

        String message = sameLane
                ? "reordered \"%s\" in %s".formatted(card.getTitle(), target.getName())
                : "moved \"%s\" to %s".formatted(card.getTitle(), target.getName());
        activityLog.record(BoardChangedEvent.content(boardIdOf(target), actor, message).forCard(cardId));
    }

    // -------------------------------------------------------------- internals

    private BoardSummary summarise(Board board, LocalDate today) {
        long id = board.getId();
        return new BoardSummary(
                id,
                board.getName(),
                board.getDescription(),
                lanes.countByBoardId(id),
                cards.countByLaneBoardIdAndArchivedAtIsNull(id),
                cards.countByLaneBoardIdAndLaneDoneFalseAndArchivedAtIsNullAndDueDateBefore(id, today),
                cards.countByLaneBoardIdAndArchivedAtIsNotNull(id),
                board.getCreatedAt());
    }

    private BoardSnapshot snapshot(Board board) {
        long id = board.getId();
        Map<Long, List<CardSnapshot>> cardsByLane = cards.findAllByLaneBoardIdAndArchivedAtIsNullOrderByPositionAsc(id).stream()
                .map(CardSnapshot::of)
                .collect(groupingBy(CardSnapshot::laneId));
        List<LaneSnapshot> laneSnapshots = lanes.findAllByBoardIdOrderByPositionAsc(id).stream()
                .map(lane -> LaneSnapshot.of(lane, cardsByLane.getOrDefault(lane.getId(), List.of())))
                .toList();
        return new BoardSnapshot(id, board.getName(), board.getDescription(), laneSnapshots);
    }

    private Board requireBoard(long boardId) {
        return boards.findById(boardId).orElseThrow(() -> new NotFoundException("Board", boardId));
    }

    private Lane requireLane(long laneId) {
        return lanes.findById(laneId).orElseThrow(() -> new NotFoundException("Lane", laneId));
    }

    private Card requireCard(long cardId) {
        return cards.findById(cardId).orElseThrow(() -> new NotFoundException("Card", cardId));
    }

    /** Tells somebody that a card landed on their plate. */
    private void notifyAssignment(Card card, String previousAssignee, String actor) {
        String assignee = card.getAssignee();
        if (assignee != null && (previousAssignee == null || !previousAssignee.equalsIgnoreCase(assignee))) {
            notifications.notify(assignee, actor, "assigned \"%s\" to you".formatted(card.getTitle()), boardIdOf(card.getLane()), card.getId());
        }
    }

    private void unlinkBlockersInLane(long laneId) {
        Instant now = clock.instant();
        for (Card card : cards.findAllByLaneId(laneId)) {
            cards.findAllBlockedBy(card.getId()).forEach(blocked -> blocked.removeBlocker(card, now));
        }
    }

    private void removeLane(Lane lane) {
        long boardId = boardIdOf(lane);
        lanes.delete(lane);
        renumberLanes(lanes.findAllByBoardIdOrderByPositionAsc(boardId));
    }

    private static String count(int n, String noun) {
        return n + " " + (n == 1 ? noun : noun + "s");
    }

    private static long boardIdOf(Lane lane) {
        return lane.getBoard().getId();
    }

    private static void renumberLanes(List<Lane> ordered) {
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).setPosition(i);
        }
    }

    private static void renumberCards(List<Card> ordered) {
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).setPosition(i);
        }
    }

    private static int clamp(int index, int max) {
        return Math.max(0, Math.min(index, max));
    }
}
