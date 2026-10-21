package dev.gatzka.flowboard.service;

import dev.gatzka.flowboard.domain.Card;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.repository.CardRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Votes on cards. Voting is deliberately quiet: boards refresh, but the activity feed stays clean. */
@Service
@Transactional
public class VoteService {

    private final CardRepository cards;
    private final ActivityLog activityLog;
    private final Clock clock;

    VoteService(CardRepository cards, ActivityLog activityLog, Clock clock) {
        this.cards = cards;
        this.activityLog = activityLog;
        this.clock = clock;
    }

    /** Adds the voter's vote, or removes it if it was already there. */
    public CardSnapshot toggle(long cardId, String voter) {
        Card card = cards.findById(cardId).orElseThrow(() -> new NotFoundException("Card", cardId));
        boolean voted = card.toggleVote(voter, clock.instant());
        activityLog.announce(BoardChangedEvent.content(card.getLane().getBoard().getId(), voter,
                (voted ? "voted for" : "withdrew a vote from") + " \"%s\"".formatted(card.getTitle())).forCard(cardId));
        return CardSnapshot.of(card);
    }
}
