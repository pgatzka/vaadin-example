package dev.gatzka.flowboard.service;

import dev.gatzka.flowboard.domain.Card;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.CardTemplate;
import dev.gatzka.flowboard.domain.CardTemplateDraft;
import dev.gatzka.flowboard.domain.CardTemplateSnapshot;
import dev.gatzka.flowboard.domain.ChecklistItem;
import dev.gatzka.flowboard.repository.CardRepository;
import dev.gatzka.flowboard.repository.CardTemplateRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Card templates per board: define them, make cards from them, or turn an existing card into one. */
@Service
@Transactional
public class CardTemplateService {

    private final CardTemplateRepository templates;
    private final CardRepository cards;
    private final BoardService boardService;
    private final ActivityLog activityLog;
    private final Clock clock;

    CardTemplateService(CardTemplateRepository templates, CardRepository cards, BoardService boardService,
            ActivityLog activityLog, Clock clock) {
        this.templates = templates;
        this.cards = cards;
        this.boardService = boardService;
        this.activityLog = activityLog;
        this.clock = clock;
    }

    /** Creates the template, or replaces the one with the same name. */
    public CardTemplateSnapshot save(long boardId, CardTemplateDraft draft, String actor) {
        CardTemplate template = templates.findByBoardIdAndNameIgnoreCase(boardId, draft.name())
                .map(existing -> {
                    existing.update(draft);
                    return existing;
                })
                .orElseGet(() -> templates.save(new CardTemplate(boardId, draft, clock.instant())));
        activityLog.record(BoardChangedEvent.updated(boardId, actor, "saved the card template \"%s\"".formatted(template.getName())));
        return CardTemplateSnapshot.of(template);
    }

    /** Captures an existing card (fields and checklist texts) as a template. */
    public CardTemplateSnapshot saveFromCard(long cardId, String name, String actor) {
        Card card = cards.findById(cardId).orElseThrow(() -> new NotFoundException("Card", cardId));
        CardTemplateDraft draft = new CardTemplateDraft(name, card.getTitle(), card.getDescription(), card.getPriority(),
                card.getAssignee(), card.getTags(), card.getEstimate(), card.getChecklist().stream().map(ChecklistItem::getText).toList());
        return save(card.getLane().getBoard().getId(), draft, actor);
    }

    public void delete(long templateId, String actor) {
        templates.findById(templateId).ifPresent(template -> {
            templates.delete(template);
            activityLog.record(BoardChangedEvent.updated(template.getBoardId(), actor,
                    "removed the card template \"%s\"".formatted(template.getName())));
        });
    }

    @Transactional(readOnly = true)
    public List<CardTemplateSnapshot> forBoard(long boardId) {
        return templates.findAllByBoardIdOrderByNameAsc(boardId).stream().map(CardTemplateSnapshot::of).toList();
    }

    /** Creates a card from the template at the end of the lane, checklist included. */
    public CardSnapshot instantiate(long templateId, long laneId, String actor) {
        CardTemplate template = templates.findById(templateId).orElseThrow(() -> new NotFoundException("Card template", templateId));
        CardTemplateDraft draft = template.toDraft();
        CardSnapshot created = boardService.createCard(laneId, draft.toCardDraft(), actor);
        if (draft.checklist().isEmpty()) {
            return created;
        }
        Card card = cards.findById(created.id()).orElseThrow();
        Instant now = clock.instant();
        draft.checklist().forEach(text -> card.addChecklistItem(text, now));
        cards.saveAndFlush(card);
        activityLog.record(BoardChangedEvent.content(card.getLane().getBoard().getId(), actor,
                "added %d checklist items to \"%s\" from the template \"%s\"".formatted(draft.checklist().size(), card.getTitle(), template.getName()))
                .forCard(card.getId()));
        return CardSnapshot.of(card);
    }

    public void forgetBoard(long boardId) {
        templates.deleteAllByBoardId(boardId);
    }
}
