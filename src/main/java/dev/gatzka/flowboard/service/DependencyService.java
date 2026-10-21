package dev.gatzka.flowboard.service;

import dev.gatzka.flowboard.domain.Card;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.repository.CardRepository;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** "Blocked by" links between cards. Links stay within a board and never form a cycle. */
@Service
@Transactional
public class DependencyService {

    private final CardRepository cards;
    private final ActivityLog activityLog;
    private final Clock clock;

    DependencyService(CardRepository cards, ActivityLog activityLog, Clock clock) {
        this.cards = cards;
        this.activityLog = activityLog;
        this.clock = clock;
    }

    public CardSnapshot addBlocker(long cardId, long blockerId, String actor) {
        Card card = requireCard(cardId);
        Card blocker = requireCard(blockerId);
        if (card.getId().equals(blocker.getId())) {
            throw new IllegalArgumentException("A card cannot block itself");
        }
        if (!card.getLane().getBoard().getId().equals(blocker.getLane().getBoard().getId())) {
            throw new IllegalArgumentException("Cards can only depend on cards of the same board");
        }
        if (dependsOn(blocker, card)) {
            throw new IllegalArgumentException("\"%s\" already depends on \"%s\"; that would be a cycle"
                    .formatted(blocker.getTitle(), card.getTitle()));
        }
        if (card.addBlocker(blocker, clock.instant())) {
            activityLog.record(BoardChangedEvent.content(boardIdOf(card), actor,
                    "marked \"%s\" as blocked by \"%s\"".formatted(card.getTitle(), blocker.getTitle())).forCard(cardId));
        }
        return CardSnapshot.of(card);
    }

    public CardSnapshot removeBlocker(long cardId, long blockerId, String actor) {
        Card card = requireCard(cardId);
        Card blocker = requireCard(blockerId);
        if (card.removeBlocker(blocker, clock.instant())) {
            activityLog.record(BoardChangedEvent.content(boardIdOf(card), actor,
                    "removed the dependency of \"%s\" on \"%s\"".formatted(card.getTitle(), blocker.getTitle())).forCard(cardId));
        }
        return CardSnapshot.of(card);
    }

    /** True when {@code card} depends on {@code target}, directly or through other cards. */
    static boolean dependsOn(Card card, Card target) {
        Set<Long> visited = new HashSet<>();
        Deque<Card> queue = new ArrayDeque<>(card.getBlockers());
        while (!queue.isEmpty()) {
            Card next = queue.poll();
            if (next.getId().equals(target.getId())) {
                return true;
            }
            if (visited.add(next.getId())) {
                queue.addAll(next.getBlockers());
            }
        }
        return false;
    }

    private Card requireCard(long cardId) {
        return cards.findById(cardId).orElseThrow(() -> new NotFoundException("Card", cardId));
    }

    private static long boardIdOf(Card card) {
        return card.getLane().getBoard().getId();
    }
}
