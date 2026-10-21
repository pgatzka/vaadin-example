package dev.gatzka.flowboard.ui.board;

import dev.gatzka.flowboard.domain.CardSnapshot;

/** Checklist operations the card editor can perform; each returns the refreshed card. */
interface ChecklistActions {

    CardSnapshot addItem(long cardId, String text);

    CardSnapshot setDone(long itemId, boolean done);

    CardSnapshot removeItem(long itemId);
}
