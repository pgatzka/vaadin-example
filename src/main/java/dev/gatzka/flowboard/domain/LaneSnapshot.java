package dev.gatzka.flowboard.domain;

import java.util.List;

public record LaneSnapshot(long id, String name, int position, Integer wipLimit, boolean done, List<CardSnapshot> cards) {

    public LaneSnapshot {
        cards = List.copyOf(cards);
    }

    public static LaneSnapshot of(Lane lane, List<CardSnapshot> cards) {
        return new LaneSnapshot(lane.getId(), lane.getName(), lane.getPosition(), lane.getWipLimit(), lane.isDone(), cards);
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
