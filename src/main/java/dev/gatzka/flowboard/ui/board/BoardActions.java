package dev.gatzka.flowboard.ui.board;

import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.LaneSnapshot;

/** What lane and card components can ask the board to do. Implemented by {@link BoardView}. */
interface BoardActions {

    void openCard(CardSnapshot card);

    void newCard(LaneSnapshot lane);

    /** Moves a card so that it ends up at {@code targetIndex} in the target lane. */
    void moveCard(long cardId, long targetLaneId, int targetIndex);

    void editLane(LaneSnapshot lane);

    /** Moves the lane by {@code delta} positions (negative is left). */
    void moveLane(LaneSnapshot lane, int delta);

    void deleteLane(LaneSnapshot lane);
}
