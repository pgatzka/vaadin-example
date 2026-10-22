package dev.gatzka.flowboard.ui.board;

import dev.gatzka.flowboard.domain.ActivityEntry;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.BoardSummary;
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

    /** Whether the current user follows the card's changes in their inbox. */
    boolean isWatching(long cardId);

    /** Starts or stops following the card; returns the new state. */
    boolean toggleWatch(long cardId);

    CardSnapshot setRecurrence(long cardId, Recurrence recurrence);

    /** Boards the current user may move or copy cards to, including this one. */
    List<BoardSummary> boardsForTransfer();

    java.util.Optional<BoardSnapshot> boardFor(long boardId);

    /** Moves ({@code copy == false}) or copies the card to the end of the given lane, which may be on another board. */
    CardSnapshot transferCard(long cardId, long targetLaneId, boolean copy);

    /** Turns the card into a template of its board under the given name. */
    void saveAsTemplate(long cardId, String name);

    CardSnapshot logTime(long cardId, int minutes, String note);

    CardSnapshot removeWorkEntry(long entryId);

    /** Starts the current user's timer on the card (stopping any other timer of theirs). */
    void startTimer(long cardId);

    /** Starts a focus session of the given length on the card; it stops and logs itself when the time is up. */
    void startFocus(long cardId, int minutes);

    /** Stops the current user's timer and logs the time; the refreshed card, if one was running. */
    java.util.Optional<CardSnapshot> stopTimer();

    java.util.Optional<TimerSnapshot> runningTimer();

    List<WorkEntrySnapshot> workEntries(long cardId);

    /** The card's history, newest first. */
    List<ActivityEntry> history(long cardId);

    CardSnapshot attach(long cardId, String fileName, String contentType, byte[] data);

    CardSnapshot removeAttachment(long attachmentId);

    java.util.Optional<byte[]> attachmentContent(long attachmentId);

    /** Makes the attachment the card's cover image, or clears the choice with {@code null}. */
    CardSnapshot setCover(long cardId, Long attachmentId);

    /** The milestones of the board, for the editor's picker. */
    java.util.List<dev.gatzka.flowboard.domain.MilestoneSnapshot> milestones();

    /** Puts the card into a milestone, or into none with {@code null}. */
    void setMilestone(long cardId, Long milestoneId);

    CardSnapshot addBlocker(long cardId, long blockerId);

    CardSnapshot removeBlocker(long cardId, long blockerId);

    /** Whether the AI assistant is configured. */
    boolean assistantAvailable();

    /** Opens the assistant's checklist suggestions for the card. */
    void suggestChecklist(CardSnapshot card);
}
