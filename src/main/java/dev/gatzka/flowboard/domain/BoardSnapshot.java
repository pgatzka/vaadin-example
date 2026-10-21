package dev.gatzka.flowboard.domain;

import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** An immutable, fully loaded view of a board: lanes in order, each with its cards in order. */
public record BoardSnapshot(long id, String name, String description, List<LaneSnapshot> lanes) {

    public BoardSnapshot {
        lanes = List.copyOf(lanes);
    }

    public int cardCount() {
        return lanes.stream().mapToInt(lane -> lane.cards().size()).sum();
    }

    public Optional<LaneSnapshot> lane(long laneId) {
        return lanes.stream().filter(lane -> lane.id() == laneId).findFirst();
    }

    /** Distinct assignees across the board, used as suggestions in the card editor. */
    public SortedSet<String> assignees() {
        return cards().map(CardSnapshot::assignee)
                .filter(assignee -> assignee != null)
                .collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));
    }

    /** Distinct tags across the board, used as suggestions in the card editor. */
    public SortedSet<String> tags() {
        return cards().flatMap(card -> card.tags().stream())
                .collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));
    }

    public BoardDraft toDraft() {
        return new BoardDraft(name, description);
    }

    private java.util.stream.Stream<CardSnapshot> cards() {
        return lanes.stream().flatMap(lane -> lane.cards().stream());
    }
}
