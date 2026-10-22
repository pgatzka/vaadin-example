package dev.gatzka.flowboard.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.gatzka.flowboard.domain.Health.Finding;
import dev.gatzka.flowboard.domain.Health.Kind;
import dev.gatzka.flowboard.domain.Health.Severity;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HealthTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);
    private static final Instant NOW = Instant.parse("2026-10-22T12:00:00Z");
    private static final Forecast NO_FORECAST = Forecast.of(0, List.of(), TODAY, null, 1);

    @Test
    void aTidyBoardIsAllClearWithFullScore() {
        BoardSnapshot board = new BoardSnapshot(1, "Tidy", null, List.of(
                new LaneSnapshot(10, "Todo", 0, null, false, List.of(card(1, 10, "fine", Priority.MEDIUM, TODAY.plusDays(3), "Mara", NOW, List.of(), null))),
                new LaneSnapshot(11, "Done", 1, null, true, List.of(card(2, 11, "old but done", Priority.URGENT, TODAY.minusDays(9), null, NOW.minus(30, ChronoUnit.DAYS), List.of(), null)))));

        Health health = Health.of(board, TODAY, NOW, NO_FORECAST, List.of());

        assertThat(health.isClear()).isTrue();
        assertThat(health.score()).isEqualTo(100);
    }

    @Test
    void findsTheUsualSuspectsAndOrdersWarningsFirst() {
        CardSnapshot blocker = card(1, 10, "Blocker", Priority.LOW, null, "Mara", NOW, List.of(), null);
        CardSnapshot blockedOne = card(2, 10, "Blocked one", Priority.URGENT, TODAY.minusDays(1), null, NOW.minus(10, ChronoUnit.DAYS),
                List.of(new BlockerSnapshot(1, "Blocker", false)), 5L);
        CardSnapshot blockedTwo = card(3, 11, "Blocked two", Priority.MEDIUM, null, "Jonas", NOW, List.of(new BlockerSnapshot(1, "Blocker", false)), null);
        CardSnapshot extra = card(4, 11, "Extra", Priority.MEDIUM, null, null, NOW, List.of(), 5L);
        BoardSnapshot board = new BoardSnapshot(1, "Busy", null, List.of(
                new LaneSnapshot(10, "Todo", 0, null, false, List.of(blocker, blockedOne)),
                new LaneSnapshot(11, "Doing", 1, 1, false, List.of(blockedTwo, extra)),
                new LaneSnapshot(12, "Done", 2, null, true, List.of())));
        MilestoneSnapshot late = new MilestoneSnapshot(5, 1, "Beta", TODAY.minusDays(2));
        List<MilestoneProgress> milestones = List.of(new MilestoneProgress(late, 0, 2, Forecast.of(2, Collections.nCopies(10, 1), TODAY, late.targetDate(), 1)));
        Forecast forecast = Forecast.of(40, List.of(1, 0, 0, 0, 0, 0, 0, 1, 0, 1), TODAY, TODAY.plusDays(3), 1);

        Health health = Health.of(board, TODAY, NOW, forecast, milestones);

        assertThat(health.findings()).extracting(Finding::kind, Finding::severity, Finding::count).containsExactly(
                tuple(Kind.OVERDUE, Severity.WARN, 1),
                tuple(Kind.MILESTONE_LATE, Severity.WARN, 2),
                tuple(Kind.WIP_OVER, Severity.WARN, 2),
                tuple(Kind.UNASSIGNED_URGENT, Severity.WARN, 1),
                tuple(Kind.KEY_BLOCKER, Severity.WARN, 2),
                tuple(Kind.FORECAST_LATE, Severity.WARN, (int) Math.round(forecast.chanceBy(TODAY.plusDays(3)) * 100)),
                tuple(Kind.STALE, Severity.INFO, 1),
                tuple(Kind.BLOCKED, Severity.INFO, 2));
        assertThat(health.findings().get(0).cardIds()).containsExactly(2L);
        assertThat(health.findings().get(0).subject()).as("a single card is named").isEqualTo("Blocked one");
        assertThat(health.findings().get(1).cardIds()).as("the milestone's open cards").containsExactly(2L, 4L);
        assertThat(health.findings().get(2).subject()).isEqualTo("Doing");
        assertThat(health.findings().get(4).subject()).isEqualTo("Blocker");
        assertThat(health.findings().get(5).hasCards()).isFalse();
        assertThat(health.findings().get(7).cardIds()).containsExactly(2L, 3L);
        assertThat(health.warnings()).isEqualTo(6);
        assertThat(health.score()).isEqualTo(0);
    }

    @Test
    void milestoneRiskDependsOnTheChance() {
        MilestoneSnapshot safe = new MilestoneSnapshot(1, 1, "Safe", TODAY.plusDays(60));
        MilestoneSnapshot shaky = new MilestoneSnapshot(2, 1, "Shaky", TODAY.plusDays(9));
        MilestoneSnapshot doomed = new MilestoneSnapshot(3, 1, "Doomed", TODAY.plusDays(1));
        List<Integer> pace = List.of(1, 0, 0, 0, 1, 0, 0, 0, 1, 0);
        List<MilestoneProgress> milestones = List.of(
                new MilestoneProgress(safe, 0, 2, Forecast.of(2, pace, TODAY, safe.targetDate(), 1)),
                new MilestoneProgress(shaky, 0, 2, Forecast.of(2, pace, TODAY, shaky.targetDate(), 1)),
                new MilestoneProgress(doomed, 0, 2, Forecast.of(2, pace, TODAY, doomed.targetDate(), 1)));
        BoardSnapshot board = new BoardSnapshot(1, "Plan", null, List.of(new LaneSnapshot(10, "Todo", 0, null, false, List.of())));

        Health health = Health.of(board, TODAY, NOW, NO_FORECAST, milestones);

        assertThat(health.findings()).extracting(Finding::kind, Finding::subject, Finding::severity).containsExactly(
                tuple(Kind.MILESTONE_AT_RISK, "Doomed", Severity.WARN),
                tuple(Kind.MILESTONE_AT_RISK, "Shaky", Severity.INFO));
        assertThat(health.score()).isEqualTo(80);
    }

    private static CardSnapshot card(long id, long laneId, String title, Priority priority, LocalDate due, String assignee, Instant enteredLane,
            List<BlockerSnapshot> blockers, Long milestoneId) {
        return new CardSnapshot(id, laneId, title, "", priority, null, due, assignee, Set.of(), null, null, 0, Set.of(),
                blockers, List.of(), List.of(), List.of(), 0, NOW, null, enteredLane, null, milestoneId);
    }
}
