package dev.gatzka.flowboard.service;

import dev.gatzka.flowboard.domain.Card;
import dev.gatzka.flowboard.domain.Forecast;
import dev.gatzka.flowboard.domain.Milestone;
import dev.gatzka.flowboard.domain.MilestoneDraft;
import dev.gatzka.flowboard.domain.MilestoneProgress;
import dev.gatzka.flowboard.domain.MilestoneSnapshot;
import dev.gatzka.flowboard.repository.CardRepository;
import dev.gatzka.flowboard.repository.MilestoneRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Milestones of a board and which cards count towards them. Progress comes with a forecast: the
 * board's throughput replayed for the milestone's open cards against its target date.
 */
@Service
@Transactional
public class MilestoneService {

    private final MilestoneRepository milestones;
    private final CardRepository cards;
    private final InsightsService insights;
    private final ActivityLog activityLog;
    private final Clock clock;

    MilestoneService(MilestoneRepository milestones, CardRepository cards, InsightsService insights, ActivityLog activityLog, Clock clock) {
        this.milestones = milestones;
        this.cards = cards;
        this.insights = insights;
        this.activityLog = activityLog;
        this.clock = clock;
    }

    public MilestoneSnapshot create(long boardId, MilestoneDraft draft, String actor) {
        Milestone milestone = milestones.save(new Milestone(boardId, draft, clock.instant()));
        activityLog.record(BoardChangedEvent.updated(boardId, actor, "added the milestone \"%s\"".formatted(milestone.getName())));
        return MilestoneSnapshot.of(milestone);
    }

    public MilestoneSnapshot update(long milestoneId, MilestoneDraft draft, String actor) {
        Milestone milestone = require(milestoneId);
        milestone.update(draft);
        activityLog.record(BoardChangedEvent.updated(milestone.getBoardId(), actor, "changed the milestone \"%s\"".formatted(milestone.getName())));
        return MilestoneSnapshot.of(milestone);
    }

    /** Removes the milestone; its cards stay and simply belong to no milestone any more. */
    public void delete(long milestoneId, String actor) {
        Milestone milestone = require(milestoneId);
        cards.clearMilestone(milestoneId);
        milestones.delete(milestone);
        activityLog.record(BoardChangedEvent.updated(milestone.getBoardId(), actor, "removed the milestone \"%s\"".formatted(milestone.getName())));
    }

    @Transactional(readOnly = true)
    public List<MilestoneSnapshot> forBoard(long boardId) {
        return ordered(boardId).stream().map(MilestoneSnapshot::of).toList();
    }

    @Transactional(readOnly = true)
    public Optional<MilestoneSnapshot> find(long milestoneId) {
        return milestones.findById(milestoneId).map(MilestoneSnapshot::of);
    }

    /** Puts the card into the milestone, or into none with {@code null}; the milestone must belong to the card's board. */
    public void assign(long cardId, Long milestoneId, String actor) {
        Card card = cards.findById(cardId).orElseThrow(() -> new NotFoundException("Card", cardId));
        long boardId = card.getLane().getBoard().getId();
        Milestone milestone = milestoneId == null ? null : require(milestoneId);
        if (milestone != null && milestone.getBoardId() != boardId) {
            throw new IllegalArgumentException("The milestone belongs to another board");
        }
        if (java.util.Objects.equals(card.getMilestoneId(), milestoneId)) {
            return;
        }
        card.setMilestone(milestoneId, clock.instant());
        String message = milestone == null
                ? "took \"%s\" out of its milestone".formatted(card.getTitle())
                : "put \"%s\" into the milestone \"%s\"".formatted(card.getTitle(), milestone.getName());
        activityLog.record(BoardChangedEvent.content(boardId, actor, message).forCard(cardId));
    }

    /** Every milestone of the board with its card counts and forecast, in the board's milestone order. */
    @Transactional(readOnly = true)
    public List<MilestoneProgress> progress(long boardId) {
        List<Milestone> boardMilestones = ordered(boardId);
        if (boardMilestones.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Card>> cardsByMilestone = cards.findAllByLaneBoardIdAndArchivedAtIsNullOrderByPositionAsc(boardId).stream()
                .filter(card -> card.getMilestoneId() != null)
                .collect(Collectors.groupingBy(Card::getMilestoneId));
        List<Integer> throughput = insights.throughput(boardId);
        LocalDate today = LocalDate.now(clock);
        return boardMilestones.stream().map(milestone -> {
            List<Card> members = cardsByMilestone.getOrDefault(milestone.getId(), List.of());
            int done = (int) members.stream().filter(card -> card.getLane().isDone()).count();
            int open = members.size() - done;
            Forecast forecast = Forecast.of(open, throughput, today, milestone.getTargetDate(), milestone.getId());
            return new MilestoneProgress(MilestoneSnapshot.of(milestone), done, open, forecast);
        }).toList();
    }

    /** Dated milestones first in date order, undated ones after them, ties by name. */
    private List<Milestone> ordered(long boardId) {
        return milestones.findAllByBoardId(boardId).stream()
                .sorted(Comparator.comparing(Milestone::getTargetDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Milestone::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private Milestone require(long milestoneId) {
        return milestones.findById(milestoneId).orElseThrow(() -> new NotFoundException("Milestone", milestoneId));
    }
}
