package dev.gatzka.flowboard.service;

import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardLocation;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.Workload;
import dev.gatzka.flowboard.repository.CardRepository;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The team's workload across boards, and the one change the team page makes: handing a card to somebody else. */
@Service
@Transactional(readOnly = true)
public class WorkloadService {

    private final CardRepository cards;
    private final BoardService boards;

    WorkloadService(CardRepository cards, BoardService boards) {
        this.cards = cards;
        this.boards = boards;
    }

    /** Open cards (not archived, not in a done lane) of the given boards, bucketed by person and week. */
    public Workload workload(Set<Long> boardIds, LocalDate today) {
        return Workload.of(cards.findAllByArchivedAtIsNull().stream()
                .filter(card -> boardIds.contains(card.getLane().getBoard().getId()))
                .filter(card -> !card.getLane().isDone())
                .map(CardLocation::of)
                .toList(), today);
    }

    /** Gives the card to {@code assignee}; an empty name clears the assignment. */
    @Transactional
    public CardSnapshot reassign(long cardId, String assignee, String actor) {
        CardSnapshot card = cards.findById(cardId).map(CardSnapshot::of).orElseThrow(() -> new NotFoundException("Card", cardId));
        CardDraft draft = card.toDraft();
        String name = assignee == null || assignee.isBlank() ? null : assignee.strip();
        return boards.updateCard(cardId, draft.withAssignee(name), actor);
    }
}
