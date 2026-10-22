package dev.gatzka.flowboard.ui.board;

import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.CardTemplateSnapshot;
import dev.gatzka.flowboard.domain.LaneRuleSnapshot;
import java.util.List;
import dev.gatzka.flowboard.domain.LaneSnapshot;

/** What lane and card components can ask the board to do. Implemented by {@link BoardView}. */
interface BoardActions {

    void openCard(CardSnapshot card);

    /** Opens the full card editor for a new card, optionally pre-filled with a title. */
    void newCard(LaneSnapshot lane, String initialTitle);

    /** Creates a card with just a title and default values, without opening the editor. */
    void quickAddCard(LaneSnapshot lane, String title);

    /** Creates several title-only cards at once, in the given order. */
    void quickAddCards(LaneSnapshot lane, List<String> titles);

    /** Remembers which lane has its quick-add composer open so it survives a re-render. */
    void composerOpened(LaneSnapshot lane);

    void composerClosed(LaneSnapshot lane);

    /** Moves a card so that it ends up at {@code targetIndex} in the target lane. */
    void moveCard(long cardId, long targetLaneId, int targetIndex);

    /** Like {@link #moveCard} but also moves the card into the swimlane {@code row} of the active grouping. */
    void moveCardToRow(long cardId, long targetLaneId, int targetIndex, String row);

    void editLane(LaneSnapshot lane);

    /** The automation rules of a lane, for the lightning badge and the lane dialog. */
    List<LaneRuleSnapshot> rulesFor(LaneSnapshot lane);

    /** Moves the lane by {@code delta} positions (negative is left). */
    void moveLane(LaneSnapshot lane, int delta);

    /** Moves the lane so that it takes {@code targetIndex} among the board's lanes. */
    void moveLaneTo(long laneId, int targetIndex);

    void deleteLane(LaneSnapshot lane);

    /** Archives every card in the lane. */
    void archiveLane(LaneSnapshot lane);

    /** Reorders the lane by votes. */
    void sortLaneByVotes(LaneSnapshot lane);

    /** Adds or withdraws the current user's vote on the card. */
    void toggleVote(CardSnapshot card);

    /** Keyboard move of a focused card: {@code ArrowUp}/{@code ArrowDown} reorder, {@code ArrowLeft}/{@code ArrowRight} change lane. */
    void moveCardByKeyboard(CardSnapshot card, String arrowKey);

    /** The board's card templates, for the lane menu. */
    java.util.List<CardTemplateSnapshot> templates();

    /** Creates a card from a template at the end of the lane. */
    void newCardFromTemplate(LaneSnapshot lane, CardTemplateSnapshot template);

    /** Opens the quick-add composer of the lane holding the card, e.g. after pressing {@code n} on it. */
    void addCardNear(CardSnapshot card);

    /** Adds the card to or removes it from the multi-selection. */
    void toggleSelection(CardSnapshot card);

    boolean isSelected(CardSnapshot card);

    /** Adds every card of the lane to the selection. */
    void selectAll(LaneSnapshot lane);

    /** The current user's display name, to show their own vote as such. */
    String currentUser();

    /** When the current user last opened this board before now, or {@code null} on their first visit. */
    java.time.Instant lastVisit();
}
