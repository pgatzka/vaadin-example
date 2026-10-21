package dev.gatzka.flowboard.domain;

import java.util.List;

public record LaneSnapshot(long id, String name, int position, Integer wipLimit, boolean done, List<CardSnapshot> cards) {

    public LaneSnapshot {
        cards = List.copyOf(cards);
    }

    public static LaneSnapshot of(Lane lane, List<CardSnapshot> cards) {
        return new LaneSnapshot(lane.getId(), lane.getName(), lane.getPosition(), lane.getWipLimit(), lane.isDone(), cards);
    }

    /** Sum of the estimates of the lane's cards; zero when nothing is estimated. */
    public int totalEstimate() {
        return cards.stream().filter(CardSnapshot::isEstimated).mapToInt(CardSnapshot::estimate).sum();
    }

    public boolean hasEstimates() {
        return cards.stream().anyMatch(CardSnapshot::isEstimated);
    }

    public boolean hasWipLimit() {
        return wipLimit != null;
    }

    public boolean isOverWipLimit() {
        return hasWipLimit() && cards.size() > wipLimit;
    }

    public LaneDraft toDraft() {
        return new LaneDraft(name, wipLimit, done);
    }
}
