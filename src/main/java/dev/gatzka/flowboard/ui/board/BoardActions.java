package dev.gatzka.flowboard.ui.board;

import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.LaneSnapshot;

/** What lane and card components can ask the board to do. Implemented by {@link BoardView}. */
interface BoardActions {

    void openCard(CardSnapshot card);

    /** Opens the full card editor for a new card, optionally pre-filled with a title. */
    void newCard(LaneSnapshot lane, String initialTitle);

    /** Creates a card with just a title and default values, without opening the editor. */
    void quickAddCard(LaneSnapshot lane, String title);

    /** Remembers which lane has its quick-add composer open so it survives a re-render. */
    void composerOpened(LaneSnapshot lane);

    void composerClosed(LaneSnapshot lane);

    /** Moves a card so that it ends up at {@code targetIndex} in the target lane. */
    void moveCard(long cardId, long targetLaneId, int targetIndex);

    void editLane(LaneSnapshot lane);

    /** Moves the lane by {@code delta} positions (negative is left). */
    void moveLane(LaneSnapshot lane, int delta);

    void deleteLane(LaneSnapshot lane);
}
