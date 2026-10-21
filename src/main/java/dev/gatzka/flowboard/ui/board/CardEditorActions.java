package dev.gatzka.flowboard.ui.board;

import dev.gatzka.flowboard.domain.ActivityEntry;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.Recurrence;
import dev.gatzka.flowboard.domain.TimerSnapshot;
import dev.gatzka.flowboard.domain.WorkEntrySnapshot;
import java.util.List;

/** Operations the card editor performs on an existing card; mutations return the refreshed card. */
interface CardEditorActions {

    CardSnapshot addChecklistItem(long cardId, String text);

    CardSnapshot setChecklistItemDone(long itemId, boolean done);

    CardSnapshot removeChecklistItem(long itemId);

    CardSnapshot addComment(long cardId, String text);

    CardSnapshot setRecurrence(long cardId, Recurrence recurrence);

    /** Turns the card into a template of its board under the given name. */
    void saveAsTemplate(long cardId, String name);

    CardSnapshot logTime(long cardId, int minutes, String note);

    CardSnapshot removeWorkEntry(long entryId);

    /** Starts the current user's timer on the card (stopping any other timer of theirs). */
    void startTimer(long cardId);

    /** Stops the current user's timer and logs the time; the refreshed card, if one was running. */
    java.util.Optional<CardSnapshot> stopTimer();

    java.util.Optional<TimerSnapshot> runningTimer();

    List<WorkEntrySnapshot> workEntries(long cardId);

    /** The card's history, newest first. */
    List<ActivityEntry> history(long cardId);

    CardSnapshot attach(long cardId, String fileName, String contentType, byte[] data);

    CardSnapshot removeAttachment(long attachmentId);

    java.util.Optional<byte[]> attachmentContent(long attachmentId);

    CardSnapshot addBlocker(long cardId, long blockerId);

    CardSnapshot removeBlocker(long cardId, long blockerId);

    /** Whether the AI assistant is configured. */
    boolean assistantAvailable();

    /** Opens the assistant's checklist suggestions for the card. */
    void suggestChecklist(CardSnapshot card);
}
