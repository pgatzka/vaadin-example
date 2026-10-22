package dev.gatzka.flowboard.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gatzka.flowboard.domain.Timeline.Bar;
import dev.gatzka.flowboard.domain.Timeline.Group;
import dev.gatzka.flowboard.domain.Timeline.GroupBy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TimelineTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22); // Thursday
    private static final Instant NOW = Instant.parse("2026-10-22T08:00:00Z");

    @Test
    void windowStartsOnTheMondayOfThePreviousWeek() {
        assertThat(Timeline.windowStart(TODAY)).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(Timeline.windowStart(LocalDate.of(2026, 10, 19))).isEqualTo(LocalDate.of(2026, 10, 12));
    }

    @Test
    void laysOutBarsMilestonesAndOpenEndsAndCountsTheRest() {
        LocalDate from = Timeline.windowStart(TODAY);
        BoardSnapshot board = new BoardSnapshot(1, "Plan", "", List.of(
                new LaneSnapshot(10, "Todo", 0, null, false, List.of(
                        card(1, "Spec", "Mara", from.plusDays(2), from.plusDays(6)),
                        card(2, "Launch", "Jonas", null, from.plusDays(20)),
                        card(3, "Research", null, from.plusDays(10), null),
                        card(4, "Someday", "Mara", null, null),
                        card(5, "Long ago", "Mara", from.minusDays(30), from.minusDays(20)),
                        card(6, "Spans the start", "Mara", from.minusDays(3), from.plusDays(1)))),
                new LaneSnapshot(20, "Done", 1, null, true, List.of(
                        card(7, "Shipped", "Jonas", from.plusDays(1), from.plusDays(3))))));

        Timeline byLane = Timeline.of(board, GroupBy.LANE, from, Timeline.DEFAULT_WEEKS);

        assertThat(byLane.to()).isEqualTo(from.plusDays(55));
        assertThat(byLane.days()).isEqualTo(56);
        assertThat(byLane.undated()).isEqualTo(1);
        assertThat(byLane.outside()).isEqualTo(1);
        assertThat(byLane.groups()).extracting(Group::name).containsExactly("Todo", "Done");
        Group todo = byLane.groups().getFirst();
        assertThat(todo.bars()).extracting(bar -> bar.card().title()).containsExactly("Spans the start", "Spec", "Research", "Launch");
        Bar spec = todo.bars().get(1);
        assertThat(spec.offsetDays(from)).isEqualTo(2);
        assertThat(spec.lengthDays()).isEqualTo(5);
        assertThat(spec.milestone()).isFalse();
        Bar clipped = todo.bars().getFirst();
        assertThat(clipped.start()).as("clipped to the window").isEqualTo(from);
        assertThat(clipped.lengthDays()).isEqualTo(2);
        Bar launch = todo.bars().get(3);
        assertThat(launch.milestone()).isTrue();
        assertThat(launch.lengthDays()).isEqualTo(1);
        Bar research = todo.bars().get(2);
        assertThat(research.openEnded()).isTrue();
        assertThat(research.end()).isEqualTo(byLane.to());
        assertThat(byLane.groups().get(1).bars().getFirst().done()).isTrue();
        assertThat(byLane.barCount()).isEqualTo(5);
        assertThat(byLane.offsetOf(TODAY)).isEqualTo(10);
        assertThat(byLane.offsetOf(from.minusDays(1))).isEqualTo(-1);
        assertThat(byLane.weekStarts()).hasSize(8).first().isEqualTo(from);

        Timeline byPerson = Timeline.of(board, GroupBy.PERSON, from, Timeline.DEFAULT_WEEKS);
        assertThat(byPerson.groups()).extracting(Group::name).containsExactly("Jonas", "Mara", "Unassigned");
        assertThat(byPerson.groups().get(2).bars()).extracting(bar -> bar.card().title()).containsExactly("Research");

        assertThat(new Bar(card(9, "Late", null, null, TODAY.minusDays(1)), "Todo", false, TODAY.minusDays(1), TODAY.minusDays(1), true, false)
                .isOverdue(TODAY)).isTrue();
        assertThat(new Bar(card(9, "Late", null, null, TODAY.minusDays(1)), "Done", true, TODAY.minusDays(1), TODAY.minusDays(1), true, false)
                .isOverdue(TODAY)).isFalse();
    }

    private static CardSnapshot card(long id, String title, String assignee, LocalDate start, LocalDate due) {
        return new CardSnapshot(id, 10, title, "", Priority.MEDIUM, start, due, assignee, Set.of(), null, null, 0, Set.of(),
                List.of(), List.of(), List.of(), List.of(), 0, NOW, null, NOW, null);
    }
}
