package dev.gatzka.flowboard.ui.board;

import dev.gatzka.flowboard.domain.ActivityEntry;
import dev.gatzka.flowboard.domain.CardSnapshot;
import java.util.List;

/** Operations the card editor performs on an existing card; mutations return the refreshed card. */
interface CardEditorActions {

    CardSnapshot addChecklistItem(long cardId, String text);

    CardSnapshot setChecklistItemDone(long itemId, boolean done);

    CardSnapshot removeChecklistItem(long itemId);

    CardSnapshot addComment(long cardId, String text);

    /** The card's history, newest first. */
    List<ActivityEntry> history(long cardId);

    CardSnapshot addBlocker(long cardId, long blockerId);

    CardSnapshot removeBlocker(long cardId, long blockerId);

    /** Whether the AI assistant is configured. */
    boolean assistantAvailable();

    /** Opens the assistant's checklist suggestions for the card. */
    void suggestChecklist(CardSnapshot card);
}
