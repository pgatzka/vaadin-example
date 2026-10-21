package dev.gatzka.flowboard.domain;

/** A card together with where it lives, for lists that span boards. */
public record CardLocation(long boardId, String boardName, String laneName, boolean laneDone, CardSnapshot card) {

    public static CardLocation of(Card card) {
        Lane lane = card.getLane();
        return new CardLocation(lane.getBoard().getId(), lane.getBoard().getName(), lane.getName(), lane.isDone(), CardSnapshot.of(card));
    }

    public boolean isCompleted() {
        return laneDone;
    }
}
