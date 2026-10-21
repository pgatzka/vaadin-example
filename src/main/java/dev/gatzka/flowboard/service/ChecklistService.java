package dev.gatzka.flowboard.service;

import dev.gatzka.flowboard.domain.Card;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.ChecklistItem;
import dev.gatzka.flowboard.repository.CardRepository;
import dev.gatzka.flowboard.repository.ChecklistItemRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Checklist items on cards. Every change is applied immediately and announced like any other. */
@Service
@Transactional
public class ChecklistService {

    private final CardRepository cards;
    private final ChecklistItemRepository items;
    private final ActivityLog activityLog;
    private final Clock clock;

    ChecklistService(CardRepository cards, ChecklistItemRepository items, ActivityLog activityLog, Clock clock) {
        this.cards = cards;
        this.items = items;
        this.activityLog = activityLog;
        this.clock = clock;
    }

    public CardSnapshot addItem(long cardId, String text, String actor) {
        Card card = cards.findById(cardId).orElseThrow(() -> new NotFoundException("Card", cardId));
        ChecklistItem item = card.addChecklistItem(text, clock.instant());
        items.saveAndFlush(item);
        record(card, actor, "added \"%s\" to the checklist of \"%s\"".formatted(item.getText(), card.getTitle()));
        return CardSnapshot.of(card);
    }

    public CardSnapshot setDone(long itemId, boolean done, String actor) {
        ChecklistItem item = requireItem(itemId);
        Card card = item.getCard();
        if (item.isDone() != done) {
            item.setDone(done);
            card.touch(clock.instant());
            record(card, actor, (done ? "ticked off" : "reopened") + " \"%s\" on \"%s\"".formatted(item.getText(), card.getTitle()));
        }
        return CardSnapshot.of(card);
    }

    public CardSnapshot removeItem(long itemId, String actor) {
        ChecklistItem item = requireItem(itemId);
        Card card = item.getCard();
        card.removeChecklistItem(item, clock.instant());
        record(card, actor, "removed \"%s\" from the checklist of \"%s\"".formatted(item.getText(), card.getTitle()));
        return CardSnapshot.of(card);
    }

    private ChecklistItem requireItem(long itemId) {
        return items.findById(itemId).orElseThrow(() -> new NotFoundException("Checklist item", itemId));
    }

    private void record(Card card, String actor, String message) {
        activityLog.record(BoardChangedEvent.content(card.getLane().getBoard().getId(), actor, message));
    }
}
